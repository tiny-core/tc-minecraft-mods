package org.tinycore.colonybridge.logic.bridge;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.integration.ColonyRef;
import org.tinycore.colonybridge.logic.warehouse.WarehouseSnapshot;

import java.util.List;

/**
 * Dados que valem para um ciclo inteiro da ponte, agrupados para não passar dez parâmetros por método.
 * Criado pelo {@link BridgeLogic} a cada ciclo e repassado ao {@link RequestCrafter}.
 * {@code record} em Java ≈ {@code record} em C#: classe imutável só de dados.
 *
 * @param warehouse conteúdo dos racks lido uma vez no começo do ciclo (+ entregas do próprio ciclo)
 * @param stock  inventário da rede em cache (o AE2 só o atualiza no fim do tick)
 * @param taken  o que já saiu da rede neste ciclo, para descontar do {@code stock}
 * @param bridge posição da ponte ({@code BlockPos.asLong()}), usada como id no {@link DeliveryLedger}
 */
record BridgeCycle(ServerLevel level, IGrid grid, IActionSource source, ColonyRef colony,
                   List<IItemHandler> racks, WarehouseSnapshot warehouse, KeyCounter stock, KeyCounter taken,
                   DeliveryLedger ledger,
                   String colonyKey, long bridge, long now, boolean craftingEnabled) {

    /** Quanto do item a rede ainda tem neste ciclo. */
    long available(AEItemKey key) {
        return stock.get(key) - taken.get(key);
    }
}
