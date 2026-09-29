package org.tinycore.colonybridge.menu.terminal;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Slot de resultado da bancada do Terminal do Armazém. Igual ao da bancada vanilla ({@code ResultSlot}:
 * consome os ingredientes, dá as conquistas, devolve baldes etc.), mais um passo: depois de cada craft,
 * repõe a grade com itens do armazém ({@link TerminalCrafting#refill}), para dar para craftar de novo sem
 * montar a receita outra vez.
 */
final class TerminalResultSlot extends ResultSlot {

    private final WarehouseTerminalMenu menu;
    private final TerminalCrafting crafting;

    TerminalResultSlot(WarehouseTerminalMenu menu, TerminalCrafting crafting, Player player, int x, int y) {
        super(player, crafting.grid, crafting.result, 0, x, y);
        this.menu = menu;
        this.crafting = crafting;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        ItemStack[] before = crafting.snapshot();
        super.onTake(player, stack);
        List<IItemHandler> racks = menu.racks(); // null no cliente: lá só o servidor repõe
        if (racks != null) {
            menu.chargeItems(crafting.refill(before, racks));
        }
    }
}
