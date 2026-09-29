package org.tinycore.core.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * Slot "fantasma" do filtro: mostra um item, mas não aceita nem entrega itens de verdade.
 * O clique é tratado à parte em {@link AbstractGhostMenu#clicked}, que só copia 1 unidade do item
 * do cursor (o item continua com o jogador).
 */
final class GhostSlot extends TabSlot {

    GhostSlot(Container container, int index, int x, int y, BooleanSupplier visible) {
        super(container, index, x, y, visible);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
