package org.tinycore.colonybridge.logic.bridge;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.ColonyRef;
import org.tinycore.colonybridge.logic.crafting.CraftLinks;
import org.tinycore.colonybridge.logic.warehouse.RackDelivery;

import java.util.List;

/**
 * O que a ponte faz com o resultado dos seus crafts ({@link CraftLinks.Sink}):
 * <ul>
 *   <li><b>itens prontos</b> → direto nos racks do armazém; o que não couber (racks cheios, colônia fora
 *       do alcance) vai para a rede ME, para o job do AE2 nunca ficar travado. O ciclo seguinte da ponte
 *       entrega essa parte normalmente;</li>
 *   <li><b>job terminado</b> → registra a estatística e roda um ciclo já, para reatribuir o pedido;</li>
 *   <li><b>job cancelado</b> (no terminal do AE2) → libera a reserva do pedido no {@link DeliveryLedger}.</li>
 * </ul>
 * Chamado pelo AE2 na thread do servidor, fora do ciclo da ponte.
 */
public final class CraftDelivery implements CraftLinks.Sink {

    private final ColonyBridgeBlockEntity host;

    public CraftDelivery(ColonyBridgeBlockEntity host) {
        this.host = host;
    }

    @Override
    public long accept(AEItemKey key, long amount, Actionable mode) {
        if (!(host.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        List<IItemHandler> racks = racks(level);
        long toRacks = racks.isEmpty() ? 0 : RackDelivery.capacity(racks, key, amount);
        if (mode == Actionable.MODULATE && toRacks > 0) {
            toRacks -= RackDelivery.insert(racks, key, toRacks); // desconta o que não entrou de fato
            host.getStats().recordCraftDelivery(level.getGameTime(), key.getItem(), toRacks);
        }
        long rest = amount - toRacks;
        return toRacks + (rest > 0 ? toNetwork(key, rest, mode) : 0);
    }

    @Override
    public void jobEnded(CraftLinks.Job job, boolean completed) {
        if (!(host.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (completed) {
            host.getStats().recordCraftDone(level.getGameTime());
        } else {
            DeliveryLedger.get(level).releaseCrafting(job.colonyKey(), job.requestId(), host.getBlockPos().asLong());
        }
        host.requestCycle();
    }

    /** Racks do armazém da colônia onde a ponte está (vazio se não há colônia ou armazém carregado). */
    private List<IItemHandler> racks(ServerLevel level) {
        ColonyRef colony = ColonyAccess.colonyAt(level, host.getBlockPos());
        return colony == null ? List.of() : ColonyAccess.warehouseRacks(colony);
    }

    /** Sobra que não coube nos racks: vai para a rede ME (com gasto de energia, como uma inserção normal). */
    private long toNetwork(AEItemKey key, long amount, Actionable mode) {
        IGridNode node = host.getActionableNode();
        if (node == null || !node.isActive()) {
            return 0;
        }
        IGrid grid = node.getGrid();
        MEStorage inventory = grid.getStorageService().getInventory();
        if (mode == Actionable.SIMULATE) {
            return inventory.insert(key, amount, Actionable.SIMULATE, host.getActionSource());
        }
        return StorageHelper.poweredInsert(grid.getEnergyService(), inventory, key, amount, host.getActionSource());
    }
}
