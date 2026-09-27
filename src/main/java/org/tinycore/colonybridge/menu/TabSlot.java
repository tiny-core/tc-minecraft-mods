package org.tinycore.colonybridge.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

import java.util.function.BooleanSupplier;

/**
 * Slot que só aparece (e só aceita clique no cliente) quando a aba dele está aberta.
 * A tela da ponte tem abas, mas o menu tem um conjunto fixo de slots; {@code isActive} é
 * o jeito do Minecraft de esconder um slot sem removê-lo.
 */
class TabSlot extends Slot {

    private final BooleanSupplier visible;

    TabSlot(Container container, int index, int x, int y, BooleanSupplier visible) {
        super(container, index, x, y);
        this.visible = visible;
    }

    @Override
    public boolean isActive() {
        return visible.getAsBoolean();
    }
}
