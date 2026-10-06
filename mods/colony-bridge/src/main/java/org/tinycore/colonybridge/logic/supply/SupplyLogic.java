package org.tinycore.colonybridge.logic.supply;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.supply.ColonySupplyBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.ColonyRef;
import org.tinycore.colonybridge.integration.OpenRequest;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.target.TargetKind;
import org.tinycore.colonybridge.logic.target.TargetLine;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.logic.target.TargetMatcher;
import org.tinycore.colonybridge.logic.target.TargetResolver;
import org.tinycore.colonybridge.logic.warehouse.RackDelivery;
import org.tinycore.colonybridge.logic.warehouse.WarehouseItems;
import org.tinycore.colonybridge.logic.warehouse.WarehouseStock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ciclo do Abastecedor: mantém no armazém o mínimo pedido e devolve à rede ME o que passar do limite, linha
 * por linha das duas listas ({@link TargetList}):
 * <ul>
 *   <li><b>Manter no armazém</b> (item ou tag) — falta? tira da rede ME e coloca nos racks
 *       ({@link RackDelivery}). Tag: a meta vale para a <b>soma</b> dos itens da tag, e o que falta vem do
 *       item que a rede tem mais (e do seguinte, se não bastar);</li>
 *   <li><b>Excedente para o ME</b> (item, tag ou mod; "tudo" = meta 0) — sobra? tira dos racks e manda para
 *       a rede ({@link WarehouseStock}), começando pelo item com mais unidades no armazém.</li>
 * </ul>
 * O armazém é lido <b>uma vez por ciclo</b> ({@link WarehouseItems#countAll}) e as contas das linhas saem
 * desse resumo, atualizado a cada movimento (linhas que se sobrepõem veem o efeito umas das outras).
 * <p>
 * <b>Proteção contra cabo de guerra:</b> o excedente nunca tira um item que está em pedido em aberto da
 * colônia (senão a Ponte entregaria e este bloco levaria de volta). O teto {@code supplyMaxPerCycle} limita
 * quanto cada linha move por ciclo. As quantidades saem da {@link SupplyRule} (regra pura, testada).
 */
public final class SupplyLogic {

    private final ColonySupplyBlockEntity host;
    private final SupplyLineResults keepResults = new SupplyLineResults();
    private final SupplyLineResults surplusResults = new SupplyLineResults();
    /** Tempo de jogo do último movimento de qualquer linha; -1 = nenhum desde que o mundo carregou. */
    private long lastMoveTime = -1;
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";

    public SupplyLogic(ColonySupplyBlockEntity host) {
        this.host = host;
    }

    public void runCycle(ServerLevel level, IGrid grid) {
        ColonyRef colony = ColonyAccess.colonyAt(level, host.getBlockPos());
        if (colony == null) {
            colonyName = "";
            setStatus(BridgeStatus.NO_COLONY);
            return;
        }
        colonyName = ColonyAccess.colonyName(colony);
        if (!ColonyAccess.canUseBridge(colony, host.getOwner())) {
            setStatus(BridgeStatus.NO_PERMISSION);
            return;
        }
        List<IItemHandler> racks = ColonyAccess.warehouseRacks(colony);
        if (racks.isEmpty()) {
            setStatus(BridgeStatus.NO_WAREHOUSE);
            return;
        }

        Cycle cycle = new Cycle(grid, racks, ColonyAccess.openRequests(colony), WarehouseItems.countAll(racks),
                level.getGameTime());
        boolean moved = false;
        List<TargetLine> keep = host.getKeepList().lines();
        for (int i = 0; i < keep.size(); i++) {
            moved |= runKeep(cycle, i, keep.get(i));
        }
        List<TargetLine> surplus = host.getSurplusList().lines();
        for (int i = 0; i < surplus.size(); i++) {
            moved |= runSurplus(cycle, i, surplus.get(i));
        }
        if (moved) {
            lastMoveTime = cycle.now;
        }
        setStatus(moved ? BridgeStatus.WORKING : BridgeStatus.IDLE);
    }

    /** Dados de um ciclo, para não passar seis parâmetros a cada linha. */
    private record Cycle(IGrid grid, List<IItemHandler> racks, List<OpenRequest> requests,
                         Object2LongLinkedOpenCustomHashMap<ItemStack> warehouse, long now) {

        KeyCounter network() {
            return grid.getStorageService().getCachedInventory();
        }
    }

    // ---------------------------------------------------------------- manter no armazém

    /** @return true se moveu algum item */
    private boolean runKeep(Cycle c, int index, TargetLine line) {
        int target = line.amount();
        List<AEItemKey> sources = keepSources(line);
        if (target <= 0 || sources.isEmpty()) {
            keepResults.clear(index);
            return false;
        }
        long current = warehouseCount(c, line);
        KeyCounter network = c.network();
        sources.sort(Comparator.comparingLong(network::get).reversed()); // mais estoque na rede primeiro
        long[] available = new long[sources.size()];
        long networkTotal = 0;
        for (int i = 0; i < available.length; i++) {
            available[i] = network.get(sources.get(i));
            networkTotal += available[i];
        }

        long missing = SupplyRule.restock(current, target, Config.SUPPLY_MAX_PER_CYCLE.get());
        long[] take = SupplyRule.allocate(missing, available);
        long moved = 0;
        for (int i = 0; i < take.length; i++) {
            if (take[i] > 0) {
                long delivered = RackDelivery.deliver(c.grid, host.getActionSource(), sources.get(i), take[i], c.racks);
                c.warehouse.addTo(sources.get(i).toStack(), delivered);
                moved += delivered;
            }
        }
        host.getStats().recordRestocked(c.now, moved);
        current += moved;
        networkTotal -= moved; // o cache do AE2 só atualiza no fim do tick
        keepResults.set(index, current, networkTotal,
                SupplyLineStatus.of(true, current, target, networkTotal, moved, false));
        return moved > 0;
    }

    /** De onde uma linha "manter" pode puxar: o próprio item (com componentes) ou cada item da tag. */
    private static List<AEItemKey> keepSources(TargetLine line) {
        List<AEItemKey> sources = new ArrayList<>();
        if (line.spec().kind() == TargetKind.ITEM) {
            AEItemKey key = AEItemKey.of(line.item());
            if (key != null) {
                sources.add(key);
            }
        } else if (line.spec().kind() == TargetKind.TAG) {
            for (Item item : TargetResolver.tagItems(line.spec())) {
                sources.add(AEItemKey.of(item));
            }
        }
        return sources;
    }

    // ---------------------------------------------------------------- excedente para o ME

    /** @return true se moveu algum item */
    private boolean runSurplus(Cycle c, int index, TargetLine line) {
        int target = line.all() ? 0 : line.amount();
        if (!line.all() && target <= 0) {
            surplusResults.clear(index); // 0 = linha desligada; "tudo" é o jeito de devolver tudo
            return false;
        }
        // Tipos do armazém que a linha aceita, o de mais unidades primeiro; itens em pedido ficam de fora.
        List<Object2LongMap.Entry<ItemStack>> types = new ArrayList<>();
        long current = 0;
        long networkTotal = 0;
        boolean requested = false;
        KeyCounter network = c.network();
        for (Object2LongMap.Entry<ItemStack> entry : c.warehouse.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0 || !TargetMatcher.matches(line, entry.getKey(), true)) {
                continue;
            }
            current += entry.getLongValue();
            networkTotal += network.get(AEItemKey.of(entry.getKey()));
            if (isRequested(c.requests, entry.getKey())) {
                requested = true;
            } else {
                types.add(entry);
            }
        }
        if (types.isEmpty() && line.spec().kind() == TargetKind.ITEM && !line.item().isEmpty()) {
            networkTotal = network.get(AEItemKey.of(line.item())); // nada no armazém: ainda mostra a rede
        }
        types.sort(Comparator.comparingLong(Object2LongMap.Entry<ItemStack>::getLongValue).reversed());

        long excess = SupplyRule.surplus(current, target, false, Config.SUPPLY_MAX_PER_CYCLE.get());
        long[] available = types.stream().mapToLong(Object2LongMap.Entry::getLongValue).toArray();
        long[] take = SupplyRule.allocate(excess, available);
        List<ItemStack> models = types.stream().map(Object2LongMap.Entry::getKey).toList();
        long moved = 0;
        for (int i = 0; i < take.length; i++) {
            if (take[i] > 0) {
                ItemStack model = models.get(i);
                long sent = WarehouseStock.toNetwork(c.racks, model, take[i], c.grid, host.getActionSource());
                c.warehouse.addTo(model, -sent);
                moved += sent;
            }
        }
        host.getStats().recordReturned(c.now, moved);
        current -= moved;
        networkTotal += moved;
        surplusResults.set(index, current, networkTotal,
                SupplyLineStatus.of(false, current, target, networkTotal, moved, requested && moved == 0));
        return moved > 0;
    }

    /** Quanto o armazém tem de itens aceitos pela linha (item exato, tag ou mod), pelo resumo do ciclo. */
    private static long warehouseCount(Cycle c, TargetLine line) {
        long total = 0;
        for (Object2LongMap.Entry<ItemStack> entry : c.warehouse.object2LongEntrySet()) {
            if (entry.getLongValue() > 0 && TargetMatcher.matches(line, entry.getKey(), true)) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    /** true se a colônia está pedindo este item agora (então ele não pode sair do armazém). */
    private static boolean isRequested(List<OpenRequest> requests, ItemStack model) {
        for (OpenRequest request : requests) {
            if (request.accepts(model)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- leitura (tela e monitor)

    /** Resultados do último ciclo da lista "manter" ({@code keep = true}) ou "excedente". */
    public SupplyLineResults results(boolean keep) {
        return keep ? keepResults : surplusResults;
    }

    /** Tempo de jogo do último movimento, ou -1. */
    public long lastMoveTime() {
        return lastMoveTime;
    }

    public BridgeStatus getStatus() {
        return status;
    }

    public String getColonyName() {
        return colonyName;
    }

    /** Fora de um ciclo completo as contagens não valem mais, então são zeradas. */
    public void setStatus(BridgeStatus status) {
        if (status != BridgeStatus.IDLE && status != BridgeStatus.WORKING) {
            keepResults.clear();
            surplusResults.clear();
        }
        this.status = status;
    }
}
