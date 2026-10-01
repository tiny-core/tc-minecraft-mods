package org.tinycore.cloud.integration.ae2;

import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.item.ContentProbe;

/**
 * Célula de armazenamento do AE2 com conteúdo não pode ir para a nuvem: um item só carregaria milhares de
 * outros e furaria a cota (plano §6, regra 4). As células não expõem a capability de itens do NeoForge, então
 * a pergunta vai pela API do AE2 ({@code StorageCells}).
 */
public final class Ae2CellProbe implements ContentProbe {

    @Override
    public boolean hasContents(@NotNull ItemStack stack) {
        if (!StorageCells.isCellHandled(stack)) return false;
        StorageCell cell = StorageCells.getCellInventory(stack, null);
        return cell != null && cell.getStatus() != CellState.EMPTY && cell.getStatus() != CellState.ABSENT;
    }
}
