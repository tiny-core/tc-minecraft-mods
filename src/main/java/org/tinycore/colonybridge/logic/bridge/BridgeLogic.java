package org.tinycore.colonybridge.logic.bridge;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.minecolonies.api.colony.IColony;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.OpenRequest;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.warehouse.RackDelivery;
import org.tinycore.colonybridge.logic.warehouse.WarehouseStock;

import java.util.List;

/**
 * Ciclo de pedidos da Ponte. A cada ciclo:
 * 1. submete crafts cujo cálculo terminou ({@link RequestCrafter});
 * 2. confere se o dono da ponte tem permissão na colônia;
 * 3. lê os pedidos em aberto da colônia, pulando os que o {@link DeliveryLedger} marca como
 *    já entregues ou sendo craftados por outra ponte;
 * 4. desconta o que o armazém já tem ({@link WarehouseStock}); se já basta, só reatribui o pedido;
 * 5. se a rede ME cobre a falta ({@link StockSearch}, podendo juntar vários itens de uma tag) → move só a
 *    falta para os racks ({@link RackDelivery}) e reatribui;
 * 6. se não cobre → o {@link RequestCrafter} agenda autocrafting só da diferença (item exato, ou um
 *    item escolhido para pedidos por tag) e a ponte entrega tudo quando o craft terminar.
 * O resultado de cada pedido vai para o {@link CycleReport}, que a tela da ponte mostra.
 */
public final class BridgeLogic {

    private final ColonyBridgeBlockEntity host;
    private final RequestCrafter crafter;
    private final CycleReport report = new CycleReport();
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";

    public BridgeLogic(ColonyBridgeBlockEntity host) {
        this.host = host;
        this.crafter = new RequestCrafter(host);
    }

    public void runCycle(ServerLevel level, IGrid grid) {
        report.clear();
        IActionSource source = host.getActionSource();
        crafter.beginCycle(level, grid, source);

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
        BridgeCycle cycle = new BridgeCycle(level, grid, source, colony, racks,
                grid.getStorageService().getCachedInventory(), new KeyCounter(), ledger,
                ColonyAccess.colonyKey(colony), host.getBlockPos().asLong(), now, host.getSettings().craftingEnabled());

        List<OpenRequest> requests = ColonyAccess.openRequests(colony);
        int handled = 0;
        for (OpenRequest request : requests) {
            if (handled >= Config.MAX_REQUESTS_PER_CYCLE.get()) {
                report.add(request, RequestOutcome.QUEUED, null);
                continue;
            }
            RequestOutcome outcome = process(cycle, request);
            report.add(request, outcome, crafter.chosenItem(request, outcome));
            if (outcome.countsTowardLimit()) {
                handled++;
            }
        }

        setStatus(requests.isEmpty() ? BridgeStatus.IDLE : BridgeStatus.WORKING);
    }

    /**
     * Trata um pedido e diz o que aconteceu. Só mexe no que <b>falta</b>:
     * falta = pedido − o que o armazém já tem; da rede sai no máximo a falta, e o autocrafting
     * só é pedido para a parte que nem o armazém nem a rede cobrem.
     */
    private RequestOutcome process(BridgeCycle c, OpenRequest request) {
        DeliveryLedger.ClaimState claim = c.ledger().state(c.colonyKey(), request.id(), c.bridge());
        if (claim == DeliveryLedger.ClaimState.DELIVERED) {
            return RequestOutcome.WAITING_COURIER;
        }
        if (claim == DeliveryLedger.ClaimState.OTHER_BRIDGE) {
            return RequestOutcome.OTHER_BRIDGE;
        }

        long missing = request.amount() - WarehouseStock.count(c.racks(), request.deliverable()::matches);
        if (missing <= 0) {
            // O armazém já cobre o pedido (ex.: entrega anterior que o courier ainda não levou, ou pedido
            // em "nova tentativa"). Não tira nada da rede: só pede ao MineColonies para reatribuir.
            c.ledger().markDelivered(c.colonyKey(), request.id(), c.bridge(), c.now());
            ColonyAccess.reassign(c.colony(), request.token());
            return RequestOutcome.IN_WAREHOUSE;
        }

        StockSearch.Result stock = StockSearch.find(c, request, host::filterAllows, missing);
        if (stock.total() < missing) {
            // A rede não cobre tudo: crafta só a diferença e espera o craft para entregar tudo de uma vez.
            // Pedido por tag só começa craft com a rede vazia (o que houver é entregue antes).
            RequestOutcome craft = request.isExact()
                    ? crafter.craftExact(c, request, missing - stock.total())
                    : crafter.craftMatching(c, request, missing, stock.isEmpty());
            if (craft.isCraftActive()) {
                return craft;
            }
            // Nenhum craft desta ponte está rodando para o pedido: solta a reserva, senão outra ponte
            // (de outra rede ME) ficaria bloqueada até ela expirar.
            c.ledger().releaseCrafting(c.colonyKey(), request.id(), c.bridge());
            if (stock.isEmpty()) {
                return stock.filtered() && craft != RequestOutcome.CRAFTING_DISABLED ? RequestOutcome.FILTERED : craft;
            }
            // Não deu para craftar (sem receita, falha, desligado): entrega o que houver na rede.
        }
        return deliver(c, request, stock.keys(), Math.min(stock.total(), missing));
    }

    /**
     * Move até {@code amount} da rede para os racks, item por item da lista, registra e reatribui o pedido.
     * Para no primeiro item que não couber inteiro (racks cheios).
     */
    private RequestOutcome deliver(BridgeCycle c, OpenRequest request, List<AEItemKey> keys, long amount) {
        long remaining = amount;
        for (AEItemKey key : keys) {
            long wanted = Math.min(c.available(key), remaining);
            if (wanted <= 0) {
                continue;
            }
            long delivered = RackDelivery.deliver(c.grid(), c.source(), key, wanted, c.racks());
            if (delivered > 0) {
                c.taken().add(key, delivered);
                host.getStats().recordDelivery(c.now(), key.getItem(), delivered);
                remaining -= delivered;
            }
            if (delivered < wanted || remaining <= 0) {
                break;
            }
        }
        if (remaining == amount) {
            return RequestOutcome.RACKS_FULL;
        }
        c.ledger().markDelivered(c.colonyKey(), request.id(), c.bridge(), c.now());
        ColonyAccess.reassign(c.colony(), request.token());
        return RequestOutcome.DELIVERED;
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
            crafter.tracker().clear();
        }
        if (status != BridgeStatus.IDLE && status != BridgeStatus.WORKING) {
            report.clear();
        }
        this.status = status;
    }
}
