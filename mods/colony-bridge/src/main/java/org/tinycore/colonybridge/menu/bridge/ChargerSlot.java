package org.tinycore.colonybridge.menu.bridge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.tinycore.colonybridge.registry.ModItems;

import java.util.function.BooleanSupplier;

/**
 * Slot do carregador do tablet na tela da Ponte: um slot de verdade (o tablet fica guardado no bloco), que só
 * aceita o TC Colony Tablet e só aparece na aba "Geral". {@code SlotItemHandler} é o slot do NeoForge para
 * inventários {@code IItemHandler}.
 * <p>
 * O {@link #mayPlace} repete a regra do inventário do bloco porque, no cliente, o inventário é uma cópia vazia
 * sem regras: sem isso a tela deixaria "pôr" outro item por um instante, até o servidor desfazer.
 */
final class ChargerSlot extends SlotItemHandler {

    private final BooleanSupplier visible;

    ChargerSlot(IItemHandler handler, int x, int y, BooleanSupplier visible) {
        super(handler, 0, x, y);
        this.visible = visible;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.is(ModItems.COLONY_TABLET.get());
    }

    @Override
    public boolean isActive() {
        return visible.getAsBoolean();
    }
}
