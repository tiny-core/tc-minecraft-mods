package org.tinycore.colonybridge.logic;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.minecolonies.api.colony.IColony;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.OpenRequest;

import java.util.List;

/**
 * Um ciclo:
 * 1. submete crafts cujo cálculo terminou;
 * 2. confere se o dono da ponte tem permissão na colônia;
 * 3. lê os pedidos em aberto da colônia, pulando os que o {@link DeliveryLedger} marca como
 *    já entregues ou sendo craftados por outra ponte;
 * 4. se o item existe na rede ME → move para os racks do armazém ({@link RackDelivery}) e reatribui o pedido;
 * 5. se não existe e o pedido é de um item exato → agenda autocrafting ({@link CraftingTracker}).
 * O resultado de cada pedido vai para o {@link CycleReport}, que a tela da ponte mostra.
 */
public final class BridgeLogic {

    private final ColonyBridgeBlockEntity host;
    private final CraftingTracker crafting = new CraftingTracker();
    private final CycleReport report = new CycleReport();
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";
    /** Preenchido por {@link #findInStock}: havia item compatível, mas o filtro barrou. */
    private boolean lastSearchFiltered;

    /**
     * Dados que valem para o ciclo inteiro, agrupados para não passar dez parâmetros por método.
     * {@code record} em Java ≈ {@code record} em C#: classe imutável só de dados.
     */
    private record Cycle(ServerLevel level, IGrid grid, IActionSource source, IColony colony,
                         List<IItemHandler> racks, KeyCounter stock, DeliveryLedger ledger,
                         String colonyKey, long bridgeId, long now, boolean craftingEnabled) {}

    public BridgeLogic(ColonyBridgeBlockEntity host) {
        this.host = host;
    }

    public void runCycle(ServerLevel level, IGrid grid) {
        report.clear();
        IActionSource source = host.getActionSource();
        crafting.poll(level, grid, source, host.getStats());

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

        long now = level.getGameTime();
        DeliveryLedger ledger = DeliveryLedger.get(level);
        ledger.expire(now, Config.REDELIVERY_COOLDOWN_TICKS.get());
        Cycle cycle = new Cycle(level, grid, source, colony, racks,
                grid.getStorageService().getCachedInventory(), ledger, ColonyAccess.colonyKey(colony),
                host.getBlockPos().asLong(), now, host.getSettings().craftingEnabled());

        List<OpenRequest> requests = ColonyAccess.openRequests(colony);
        int handled = 0;
        for (OpenRequest request : requests) {
            if (handled >= Config.MAX_REQUESTS_PER_CYCLE.get()) {
                report.add(request, RequestOutcome.QUEUED);
                continue;
            }
            RequestOutcome outcome = process(cycle, request);
            report.add(request, outcome);
            if (outcome.countsTowardLimit()) {
                handled++;
            }
        }

        setStatus(requests.isEmpty() ? BridgeStatus.IDLE : BridgeStatus.WORKING);
    }

    /** Trata um pedido e diz o que aconteceu. */
    private RequestOutcome process(Cycle c, OpenRequest request) {
        DeliveryLedger.ClaimState claim = c.ledger().state(c.colonyKey(), request.id(), c.bridgeId());
        if (claim == DeliveryLedger.ClaimState.DELIVERED) {
            return RequestOutcome.WAITING_COURIER;
        }
        if (claim == DeliveryLedger.ClaimState.OTHER_BRIDGE) {
            return RequestOutcome.OTHER_BRIDGE;
        }

        AEItemKey inStock = findInStock(c.stock(), request);
        boolean filtered = lastSearchFiltered;
        if (inStock != null) {
            long delivered = RackDelivery.deliver(c.grid(), c.source(), inStock, request.amount(), c.racks());
            if (delivered <= 0) {
                return RequestOutcome.RACKS_FULL;
            }
            c.ledger().markDelivered(c.colonyKey(), request.id(), c.bridgeId(), c.now());
            host.getStats().recordDelivery(c.now(), inStock.getItem(), delivered);
            ColonyAccess.reassign(c.colony(), request.token());
            return RequestOutcome.DELIVERED;
        }

        if (!request.isExact()) {
            return filtered ? RequestOutcome.FILTERED : RequestOutcome.NO_STOCK;
        }
        if (!host.filterAllows(request.exactStack())) {
            return RequestOutcome.FILTERED; // nem entrega nem crafta um item bloqueado
        }
        AEItemKey key = AEItemKey.of(request.exactStack());
        if (key == null) {
            return RequestOutcome.NOT_CRAFTABLE;
        }
        if (crafting.isBusy(c.grid().getCraftingService(), key)) {
            // Craft ainda rodando: renova a reserva (se for desta ponte) para ela não expirar
            // no meio de um craft longo e outra ponte começar o mesmo craft.
            c.ledger().renewCrafting(c.colonyKey(), request.id(), c.bridgeId(), c.now());
            return RequestOutcome.CRAFTING;
        }
        if (!c.craftingEnabled()) {
            return RequestOutcome.CRAFTING_DISABLED;
        }
        if (!crafting.tryStart(c.level(), c.grid(), c.source(), key, request.amount())) {
            return RequestOutcome.NOT_CRAFTABLE;
        }
        // Reserva o pedido: outras pontes não craftam para ele; esta entrega quando ficar pronto.
        c.ledger().markCrafting(c.colonyKey(), request.id(), c.bridgeId(), c.now());
        return RequestOutcome.CRAFT_STARTED;
    }

    /**
     * Procura na rede um item que satisfaça o pedido e que o filtro permita (exato primeiro, depois por
     * correspondência). Se achou algo compatível mas o filtro barrou, marca {@link #lastSearchFiltered}.
     */
    private @Nullable AEItemKey findInStock(KeyCounter stock, OpenRequest request) {
        lastSearchFiltered = false;
        if (request.isExact()) {
            AEItemKey exact = AEItemKey.of(request.exactStack());
            if (exact != null && stock.get(exact) > 0) {
                if (host.filterAllows(exact.getReadOnlyStack())) {
                    return exact;
                }
                lastSearchFiltered = true;
            }
        }
        // Pedidos por tag / ferramenta / comida: testa cada item da rede.
        // getReadOnlyStack() devolve um ItemStack que a própria chave guarda em cache: zero alocação
        // por item, o que importa em redes grandes do ATM10. Não pode ser modificado — matches() só lê.
        for (Object2LongMap.Entry<AEKey> entry : stock) {
            if (entry.getLongValue() > 0
                    && entry.getKey() instanceof AEItemKey itemKey
                    && request.deliverable().matches(itemKey.getReadOnlyStack())) {
                if (host.filterAllows(itemKey.getReadOnlyStack())) {
                    return itemKey;
                }
                lastSearchFiltered = true; // continua procurando outro item compatível
            }
        }
        return null;
    }

    public BridgeStatus getStatus() {
        return status;
    }

    public CycleReport getReport() {
        return report;
    }

    /** Nome da colônia encontrada no último ciclo ("" se nenhuma). */
    public String getColonyName() {
        return colonyName;
    }

    /** Muda o status; fora de um ciclo completo o relatório fica vazio (nada foi processado). */
    public void setStatus(BridgeStatus status) {
        if (status == BridgeStatus.OFFLINE) {
            crafting.clear();
        }
        if (status != BridgeStatus.IDLE && status != BridgeStatus.WORKING) {
            report.clear();
        }
        this.status = status;
    }
}
