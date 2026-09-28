package org.tinycore.colonybridge.logic.bridge;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.OpenRequest;
import org.tinycore.colonybridge.logic.crafting.CraftCandidates;
import org.tinycore.colonybridge.logic.crafting.CraftingTracker;

/**
 * Decide e agenda o autocrafting de um pedido da colônia. Separado do {@link BridgeLogic} para ele
 * cuidar só de "entregar ou craftar?" e esta classe de "craftar o quê, quanto e se dá".
 * <ul>
 *   <li><b>Pedido exato</b> (um item específico): crafta esse item.</li>
 *   <li><b>Pedido por tag/ferramenta/comida</b>: o {@link CraftCandidates} escolhe um item craftável que
 *       o pedido aceite, conforme as regras da ponte ({@code ColonyBridgeBlockEntity.craftRules()}:
 *       preferência, itens preferidos e mods), completadas pela config do servidor.</li>
 * </ul>
 * Em ambos, enquanto o craft roda o pedido fica reservado para esta ponte no {@link DeliveryLedger}
 * (renovado a cada ciclo), e o {@link CraftingTracker} guarda qual item acompanhar.
 */
final class RequestCrafter {

    private final ColonyBridgeBlockEntity host;
    private final CraftingTracker tracker = new CraftingTracker();
    private final CraftCandidates candidates = new CraftCandidates();

    RequestCrafter(ColonyBridgeBlockEntity host) {
        this.host = host;
    }

    CraftingTracker tracker() {
        return tracker;
    }

    /** Começo do ciclo: submete cálculos prontos e limpa os caches do ciclo anterior. */
    void beginCycle(ServerLevel level, IGrid grid, IActionSource source) {
        tracker.poll(level, grid, source, host.getStats());
        candidates.beginCycle();
    }

    /** true se pedidos por tag podem ser craftados (config do servidor). */
    static boolean tagCraftingEnabled() {
        return Config.TAG_CRAFTING.get();
    }

    /**
     * Item escolhido para um pedido por tag que está sendo craftado, para a tela mostrar; null nos
     * demais casos (pedido exato já mostra o próprio item).
     */
    @Nullable AEItemKey chosenItem(OpenRequest request, RequestOutcome outcome) {
        return request.isExact() || !outcome.isCraftActive() ? null : tracker.remembered(request.id());
    }

    /** Crafta {@code shortfall} unidades do item exato do pedido. */
    RequestOutcome craftExact(BridgeCycle c, OpenRequest request, long shortfall) {
        if (!host.filterAllows(request.exactStack())) {
            return RequestOutcome.FILTERED; // nem entrega nem crafta um item bloqueado
        }
        AEItemKey key = AEItemKey.of(request.exactStack());
        if (key == null) {
            return RequestOutcome.NOT_CRAFTABLE;
        }
        if (tracker.isBusy(c.grid().getCraftingService(), key)) {
            return stillCrafting(c, request);
        }
        return start(c, request, key, shortfall);
    }

    /**
     * Crafta {@code shortfall} unidades de algum item que o pedido aceite.
     *
     * @param mayStart false quando a rede ainda tem algum item compatível: só acompanha um craft já em
     *                 andamento, sem começar outro (o que há na rede será entregue antes)
     */
    RequestOutcome craftMatching(BridgeCycle c, OpenRequest request, long shortfall, boolean mayStart) {
        ICraftingService crafting = c.grid().getCraftingService();
        if (tracker.activeFor(request.id(), crafting) != null) {
            return stillCrafting(c, request);
        }
        if (!mayStart || !tagCraftingEnabled()) {
            return RequestOutcome.NO_STOCK;
        }
        if (!c.craftingEnabled()) {
            return RequestOutcome.CRAFTING_DISABLED;
        }
        @Nullable AEItemKey key = candidates.choose(crafting, host.craftRules(),
                stack -> request.deliverable().matches(stack) && host.filterAllows(stack),
                k -> tracker.canTry(c.level(), crafting, k));
        return key == null ? RequestOutcome.NOT_CRAFTABLE : start(c, request, key, shortfall);
    }

    /**
     * Craft ainda rodando: renova a reserva (se for desta ponte) para ela não expirar no meio de um
     * craft longo e outra ponte começar o mesmo craft.
     */
    private RequestOutcome stillCrafting(BridgeCycle c, OpenRequest request) {
        c.ledger().renewCrafting(c.colonyKey(), request.id(), c.bridge(), c.now());
        return RequestOutcome.CRAFTING;
    }

    private RequestOutcome start(BridgeCycle c, OpenRequest request, AEItemKey key, long shortfall) {
        if (!c.craftingEnabled()) {
            return RequestOutcome.CRAFTING_DISABLED;
        }
        if (!tracker.tryStart(c.level(), c.grid(), c.source(), key, shortfall)) {
            return RequestOutcome.NOT_CRAFTABLE;
        }
        tracker.remember(request.id(), key);
        // Reserva o pedido: outras pontes não craftam para ele; esta entrega quando ficar pronto.
        c.ledger().markCrafting(c.colonyKey(), request.id(), c.bridge(), c.now());
        return RequestOutcome.CRAFT_STARTED;
    }
}
