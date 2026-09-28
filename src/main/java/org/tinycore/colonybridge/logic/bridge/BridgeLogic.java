package org.tinycore.colonybridge.logic.bridge;

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
 * 5. se a rede ME cobre a falta → move só a falta para os racks ({@link RackDelivery}) e reatribui;
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
    /** Preenchido por {@link #findInStock}: havia item compatível, mas o filtro barrou. */
    private boolean lastSearchFiltered;

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

        AEItemKey inStock = findInStock(c, request);
        boolean filtered = lastSearchFiltered;
        if (inStock == null && !request.isExact() && !RequestCrafter.tagCraftingEnabled()) {
            // Sem estoque e sem como craftar: nem vale a pena varrer os racks.
            return filtered ? RequestOutcome.FILTERED : RequestOutcome.NO_STOCK;
        }

        long missing = request.amount() - WarehouseStock.count(c.racks(), request.deliverable()::matches);
        if (missing <= 0) {
            // O armazém já cobre o pedido (ex.: entrega anterior que o courier ainda não levou, ou pedido
            // em "nova tentativa"). Não tira nada da rede: só pede ao MineColonies para reatribuir.
            c.ledger().markDelivered(c.colonyKey(), request.id(), c.bridge(), c.now());
            ColonyAccess.reassign(c.colony(), request.token());
            return RequestOutcome.IN_WAREHOUSE;
        }

        long available = inStock == null ? 0 : c.available(inStock);
        if (available < missing) {
            // A rede não cobre tudo: crafta só a diferença e espera o craft para entregar tudo de uma vez.
            // Pedido por tag só começa craft com a rede vazia (o que houver é entregue antes).
            // Se não der para craftar (sem receita, falha, desligado), entrega o que houver na rede.
            RequestOutcome craft = request.isExact()
                    ? crafter.craftExact(c, request, missing - available)
                    : crafter.craftMatching(c, request, missing, inStock == null);
            if (craft == RequestOutcome.CRAFT_STARTED || craft == RequestOutcome.CRAFTING) {
                return craft;
            }
            if (inStock == null) {
                return filtered && craft != RequestOutcome.CRAFTING_DISABLED ? RequestOutcome.FILTERED : craft;
            }
        }
        return deliver(c, request, inStock, Math.min(available, missing));
    }

    /** Move {@code amount} da rede para os racks, registra e reatribui o pedido. */
    private RequestOutcome deliver(BridgeCycle c, OpenRequest request, AEItemKey key, long amount) {
        long delivered = RackDelivery.deliver(c.grid(), c.source(), key, amount, c.racks());
        if (delivered <= 0) {
            return RequestOutcome.RACKS_FULL;
        }
        c.taken().add(key, delivered);
        c.ledger().markDelivered(c.colonyKey(), request.id(), c.bridge(), c.now());
        host.getStats().recordDelivery(c.now(), key.getItem(), delivered);
        ColonyAccess.reassign(c.colony(), request.token());
        return RequestOutcome.DELIVERED;
    }

    /**
     * Procura na rede um item que satisfaça o pedido e que o filtro permita (exato primeiro, depois por
     * correspondência). Se achou algo compatível mas o filtro barrou, marca {@link #lastSearchFiltered}.
     */
    private @Nullable AEItemKey findInStock(BridgeCycle c, OpenRequest request) {
        lastSearchFiltered = false;
        if (request.isExact()) {
            AEItemKey exact = AEItemKey.of(request.exactStack());
            if (exact != null && c.available(exact) > 0) {
                if (host.filterAllows(exact.getReadOnlyStack())) {
                    return exact;
                }
                lastSearchFiltered = true;
            }
        }
        // Pedidos por tag / ferramenta / comida: testa cada item da rede.
        // getReadOnlyStack() devolve um ItemStack que a própria chave guarda em cache: zero alocação
        // por item, o que importa em redes grandes do ATM10. Não pode ser modificado — matches() só lê.
        for (Object2LongMap.Entry<AEKey> entry : c.stock()) {
            if (entry.getLongValue() > 0
                    && entry.getKey() instanceof AEItemKey itemKey
                    && c.available(itemKey) > 0
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
            crafter.tracker().clear();
        }
        if (status != BridgeStatus.IDLE && status != BridgeStatus.WORKING) {
            report.clear();
        }
        this.status = status;
    }
}
