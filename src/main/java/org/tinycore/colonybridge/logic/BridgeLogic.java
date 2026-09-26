package org.tinycore.colonybridge.logic;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.OpenRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Um ciclo:
 * 1. submete crafts cujo cálculo terminou;
 * 2. lê os pedidos em aberto da colónia;
 * 3. se o item existe na rede ME → move para os racks do armazém e reatribui o pedido;
 * 4. se não existe e o pedido é de um item exato → agenda autocrafting.
 */
public final class BridgeLogic {

    private final ColonyBridgeBlockEntity host;
    private final CraftingTracker crafting = new CraftingTracker();
    /** token do pedido → gameTime da última entrega (evita entregar duas vezes). */
    private final Map<IToken<?>, Long> lastDelivery = new HashMap<>();
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
        List<IItemHandler> racks = ColonyAccess.warehouseRacks(colony);
        if (racks.isEmpty()) {
            setStatus(BridgeStatus.NO_WAREHOUSE);
            return;
        }

        long now = level.getGameTime();
        long cooldown = Config.REDELIVERY_COOLDOWN_TICKS.get();
        lastDelivery.values().removeIf(t -> now - t > cooldown);

        List<OpenRequest> requests = ColonyAccess.openRequests(colony);
        KeyCounter stock = grid.getStorageService().getCachedInventory();
        int handled = 0;

        for (OpenRequest request : requests) {
            if (handled >= Config.MAX_REQUESTS_PER_CYCLE.get()) {
                break;
            }
            if (lastDelivery.containsKey(request.token())) {
                continue;
            }

            AEItemKey inStock = findInStock(stock, request);
            if (inStock != null) {
                long delivered = deliver(grid, source, inStock, request.amount(), racks);
                if (delivered > 0) {
                    lastDelivery.put(request.token(), now);
                    ColonyAccess.reassign(colony, request.token());
                    handled++;
                }
                continue;
            }

            if (request.isExact()) {
                AEItemKey key = AEItemKey.of(request.exactStack());
                if (key != null && crafting.tryStart(level, grid, source, key, request.amount())) {
                    handled++;
                }
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
        // TODO: indexar por item para não alocar um ItemStack por chave em redes grandes.
        for (Object2LongMap.Entry<AEKey> entry : stock) {
            if (entry.getLongValue() > 0
                    && entry.getKey() instanceof AEItemKey itemKey
                    && request.deliverable().matches(itemKey.toStack())) {
                return itemKey;
            }
        }
        return null;
    }

    /**
     * Move itens da rede ME para os racks.
     * Simula primeiro para só extrair o que cabe; o que sobrar volta à rede.
     *
     * @return quantidade efetivamente colocada no armazém
     */
    private static long deliver(IGrid grid, IActionSource source, AEItemKey key, long wanted,
                                List<IItemHandler> racks) {
        MEStorage inventory = grid.getStorageService().getInventory();
        IEnergySource energy = grid.getEnergyService();

        long available = StorageHelper.poweredExtraction(energy, inventory, key, wanted, source, Actionable.SIMULATE);
        if (available <= 0) {
            return 0;
        }
        long fits = available - insert(racks, key, available, true);
        if (fits <= 0) {
            return 0; // racks cheios
        }
        long extracted = StorageHelper.poweredExtraction(energy, inventory, key, fits, source, Actionable.MODULATE);
        long leftover = insert(racks, key, extracted, false);
        if (leftover > 0) {
            long returned = StorageHelper.poweredInsert(energy, inventory, key, leftover, source);
            if (returned < leftover) {
                ColonyBridgeMod.LOG.warn("Não foi possível devolver {}x {} à rede ME", leftover - returned, key);
            }
        }
        return extracted - leftover;
    }

    /** @return quantidade que NÃO coube */
    private static long insert(List<IItemHandler> racks, AEItemKey key, long amount, boolean simulate) {
        long remaining = amount;
        int maxStack = key.getMaxStackSize();
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, maxStack);
            ItemStack stack = key.toStack(chunk);
            for (IItemHandler rack : racks) {
                stack = ItemHandlerHelper.insertItemStacked(rack, stack, simulate);
                if (stack.isEmpty()) {
                    break;
                }
            }
            int inserted = chunk - stack.getCount();
            remaining -= inserted;
            if (!stack.isEmpty()) {
                break; // já não cabe mais
            }
            // Nota: em simulação, vários chunks não "ocupam" espaço entre si,
            // por isso a estimativa pode ser otimista. O excesso volta à rede no passo real.
        }
        return remaining;
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
