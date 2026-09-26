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
 */
public final class BridgeLogic {

    private final ColonyBridgeBlockEntity host;
    private final CraftingTracker crafting = new CraftingTracker();
    private BridgeStatus status = BridgeStatus.STARTING;

    public BridgeLogic(ColonyBridgeBlockEntity host) {
        this.host = host;
    }

    public void runCycle(ServerLevel level, IGrid grid) {
        IActionSource source = host.getActionSource();
        crafting.poll(level, grid, source);

        IColony colony = ColonyAccess.findColony(level, host.getBlockPos());
        if (colony == null) {
            setStatus(BridgeStatus.NO_COLONY);
            return;
        }
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
        String colonyKey = ColonyAccess.colonyKey(colony);
        long bridgeId = host.getBlockPos().asLong();

        List<OpenRequest> requests = ColonyAccess.openRequests(colony);
        KeyCounter stock = grid.getStorageService().getCachedInventory();
        int handled = 0;

        for (OpenRequest request : requests) {
            if (handled >= Config.MAX_REQUESTS_PER_CYCLE.get()) {
                break;
            }
            if (ledger.isBlocked(colonyKey, request.id(), bridgeId)) {
                continue;
            }

            AEItemKey inStock = findInStock(stock, request);
            if (inStock != null) {
                long delivered = RackDelivery.deliver(grid, source, inStock, request.amount(), racks);
                if (delivered > 0) {
                    ledger.markDelivered(colonyKey, request.id(), bridgeId, now);
                    ColonyAccess.reassign(colony, request.token());
                    handled++;
                }
                continue;
            }

            if (!request.isExact()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(request.exactStack());
            if (key == null) {
                continue;
            }
            if (crafting.isBusy(grid.getCraftingService(), key)) {
                // Craft ainda rodando: renova a reserva (se for desta ponte) para ela não expirar
                // no meio de um craft longo e outra ponte começar o mesmo craft.
                ledger.renewCrafting(colonyKey, request.id(), bridgeId, now);
                continue;
            }
            if (crafting.tryStart(level, grid, source, key, request.amount())) {
                // Reserva o pedido: outras pontes não craftam para ele; esta entrega quando ficar pronto.
                ledger.markCrafting(colonyKey, request.id(), bridgeId, now);
                handled++;
            }
        }

        setStatus(requests.isEmpty() ? BridgeStatus.IDLE : BridgeStatus.WORKING);
    }

    /** Procura na rede um item que satisfaça o pedido (exato primeiro, depois por correspondência). */
    private static @Nullable AEItemKey findInStock(KeyCounter stock, OpenRequest request) {
        if (request.isExact()) {
            AEItemKey exact = AEItemKey.of(request.exactStack());
            if (exact != null && stock.get(exact) > 0) {
                return exact;
            }
        }
        // Pedidos por tag / ferramenta / comida: testa cada item da rede.
        // getReadOnlyStack() devolve um ItemStack que a própria chave guarda em cache: zero alocação
        // por item, o que importa em redes grandes do ATM10. Não pode ser modificado — matches() só lê.
        for (Object2LongMap.Entry<AEKey> entry : stock) {
            if (entry.getLongValue() > 0
                    && entry.getKey() instanceof AEItemKey itemKey
                    && request.deliverable().matches(itemKey.getReadOnlyStack())) {
                return itemKey;
            }
        }
        return null;
    }

    public BridgeStatus getStatus() {
        return status;
    }

    public void setStatus(BridgeStatus status) {
        if (status == BridgeStatus.OFFLINE) {
            crafting.clear();
        }
        this.status = status;
    }
}
