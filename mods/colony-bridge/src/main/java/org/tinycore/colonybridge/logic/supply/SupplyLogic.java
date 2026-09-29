package org.tinycore.colonybridge.logic.supply;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.minecolonies.api.colony.IColony;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.supply.ColonySupplyBlockEntity;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.OpenRequest;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.warehouse.RackDelivery;
import org.tinycore.colonybridge.logic.warehouse.WarehouseStock;

import java.util.Arrays;
import java.util.List;

/**
 * Ciclo do bloco de abastecimento: mantém no armazém a quantidade que o jogador pediu e devolve à
 * rede ME o que passar do limite.
 * <p>
 * Para cada linha da {@link StockList}:
 * <ul>
 *   <li><b>manter no armazém</b> — falta item? tira da rede ME e coloca nos racks ({@link RackDelivery});</li>
 *   <li><b>excedente para o ME</b> — sobra item? tira dos racks e manda para a rede ({@link WarehouseStock}).</li>
 * </ul>
 * <b>Proteção contra cabo de guerra:</b> o excedente nunca sai se aquele item estiver em algum pedido
 * em aberto da colônia. Sem isso, a ponte entregaria o item e este bloco o levaria de volta, num
 * vaivém sem fim. O teto {@code supplyMaxPerCycle} limita quanto cada linha move por ciclo. As quantidades
 * saem da {@link SupplyRule} (regra pura, testada sem o jogo).
 */
public final class SupplyLogic {

    private final ColonySupplyBlockEntity host;
    private final long[] counts = new long[StockList.SIZE];
    /** Quanto a rede ME tinha de cada linha no último ciclo (para o monitor). */
    private final long[] networkCounts = new long[StockList.SIZE];
    private final SupplyLineStatus[] lineStatus = new SupplyLineStatus[StockList.SIZE];
    /** Tempo de jogo do último movimento de qualquer linha; -1 = nenhum desde que o mundo carregou. */
    private long lastMoveTime = -1;
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";

    public SupplyLogic(ColonySupplyBlockEntity host) {
        this.host = host;
        Arrays.fill(lineStatus, SupplyLineStatus.UNKNOWN);
    }

    public void runCycle(ServerLevel level, IGrid grid) {
        IColony colony = ColonyAccess.findColony(level, host.getBlockPos());
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

        List<OpenRequest> requests = ColonyAccess.openRequests(colony);
        long now = level.getGameTime();
        boolean moved = false;
        for (int slot = 0; slot < StockList.SIZE; slot++) {
            moved |= runLine(slot, grid, racks, requests, now);
        }
        if (moved) {
            lastMoveTime = now;
        }
        setStatus(moved ? BridgeStatus.WORKING : BridgeStatus.IDLE);
    }

    /**
     * Uma linha: mede armazém e rede, move o que a {@link SupplyRule} mandar e guarda o resultado para a
     * tela e o monitor (armazém <b>depois</b> do movimento e a {@link SupplyLineStatus}).
     *
     * @return true se moveu algum item
     */
    private boolean runLine(int slot, IGrid grid, List<IItemHandler> racks, List<OpenRequest> requests, long now) {
        ItemStack model = host.getStock().item(slot);
        int target = host.getStock().amount(slot);
        AEItemKey key = AEItemKey.of(model);
        if (model.isEmpty() || target <= 0 || key == null) {
            counts[slot] = 0;
            networkCounts[slot] = 0;
            lineStatus[slot] = SupplyLineStatus.UNKNOWN;
            return false;
        }
        IActionSource source = host.getActionSource();
        int perCycle = Config.SUPPLY_MAX_PER_CYCLE.get();
        boolean keep = StockList.isKeep(slot);
        boolean requested = !keep && isRequested(requests, model);
        long current = WarehouseStock.count(racks, model);
        long moved;
        if (keep) {
            long missing = SupplyRule.restock(current, target, perCycle);
            moved = missing > 0 ? RackDelivery.deliver(grid, source, key, missing, racks) : 0;
            host.getStats().recordRestocked(now, moved);
            current += moved;
        } else {
            long surplus = SupplyRule.surplus(current, target, requested, perCycle);
            moved = surplus > 0 ? WarehouseStock.toNetwork(racks, model, surplus, grid, source) : 0;
            host.getStats().recordReturned(now, moved);
            current -= moved;
        }
        counts[slot] = current;
        networkCounts[slot] = grid.getStorageService().getCachedInventory().get(key);
        lineStatus[slot] = SupplyLineStatus.of(keep, current, target, networkCounts[slot], moved, requested);
        return moved > 0;
    }

    /** true se a colônia está pedindo este item agora (então ele não pode sair do armazém). */
    private static boolean isRequested(List<OpenRequest> requests, ItemStack model) {
        for (OpenRequest request : requests) {
            if (request.deliverable().matches(model)) {
                return true;
            }
        }
        return false;
    }

    /** Quanto existe no armazém de cada linha, medido no último ciclo (para a tela). */
    public long count(int slot) {
        return slot >= 0 && slot < counts.length ? counts[slot] : 0;
    }

    /** Quanto a rede ME tinha do item da linha no último ciclo. */
    public long networkCount(int slot) {
        return slot >= 0 && slot < networkCounts.length ? networkCounts[slot] : 0;
    }

    /** Situação da linha no último ciclo. */
    public SupplyLineStatus lineStatus(int slot) {
        return slot >= 0 && slot < lineStatus.length ? lineStatus[slot] : SupplyLineStatus.UNKNOWN;
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
            Arrays.fill(counts, 0);
            Arrays.fill(networkCounts, 0);
            Arrays.fill(lineStatus, SupplyLineStatus.UNKNOWN);
        }
        this.status = status;
    }
}
