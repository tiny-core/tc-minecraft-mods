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
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";

    public SupplyLogic(ColonySupplyBlockEntity host) {
        this.host = host;
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

        IActionSource source = host.getActionSource();
        StockList stock = host.getStock();
        List<OpenRequest> requests = ColonyAccess.openRequests(colony);
        int perCycle = Config.SUPPLY_MAX_PER_CYCLE.get();
        boolean moved = false;

        long now = level.getGameTime();
        for (int slot = 0; slot < StockList.SIZE; slot++) {
            ItemStack model = stock.item(slot);
            int target = stock.amount(slot);
            if (model.isEmpty() || target <= 0) {
                counts[slot] = 0;
                continue;
            }
            long current = WarehouseStock.count(racks, model);
            if (StockList.isKeep(slot)) {
                long restocked = keepStocked(grid, source, model, SupplyRule.restock(current, target, perCycle), racks);
                host.getStats().recordRestocked(now, restocked);
                // A tela e o monitor mostram o armazém depois do movimento, não a leitura de antes.
                counts[slot] = current + restocked;
                moved |= restocked > 0;
                continue;
            }
            long surplus = current > target ? SupplyRule.surplus(current, target, isRequested(requests, model), perCycle) : 0;
            if (surplus > 0) {
                long returned = WarehouseStock.toNetwork(racks, model, surplus, grid, source);
                host.getStats().recordReturned(now, returned);
                current -= returned;
                moved |= returned > 0;
            }
            counts[slot] = current;
        }
        setStatus(moved ? BridgeStatus.WORKING : BridgeStatus.IDLE);
    }

    /** Repõe {@code amount} (já limitado pela {@link SupplyRule}), tirando da rede ME. @return quantidade colocada no armazém */
    private static long keepStocked(IGrid grid, IActionSource source, ItemStack model, long amount,
                                    List<IItemHandler> racks) {
        if (amount <= 0) {
            return 0;
        }
        AEItemKey key = AEItemKey.of(model);
        return key == null ? 0 : RackDelivery.deliver(grid, source, key, amount, racks);
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

    public BridgeStatus getStatus() {
        return status;
    }

    public String getColonyName() {
        return colonyName;
    }

    /** Fora de um ciclo completo as contagens não valem mais, então são zeradas. */
    public void setStatus(BridgeStatus status) {
        if (status != BridgeStatus.IDLE && status != BridgeStatus.WORKING) {
            java.util.Arrays.fill(counts, 0);
        }
        this.status = status;
    }
}
