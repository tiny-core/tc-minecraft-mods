package org.tinycore.colonybridge.client.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.client.ColonyBridgeScreen;
import org.tinycore.colonybridge.network.FilterSlotPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Diz ao JEI onde um item arrastado pode ser solto (os ghost slots visíveis) e o que fazer ao soltar:
 * mandar um {@link FilterSlotPayload} para o servidor, que valida e grava a cópia.
 * <p>
 * {@code <I>} é um generic de método (≈ {@code Method<T>()} em C#): o JEI trabalha com vários tipos de
 * ingrediente (itens, fluidos...); aqui só aceitamos itens.
 */
final class FilterGhostHandler implements IGhostIngredientHandler<ColonyBridgeScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(ColonyBridgeScreen screen, ITypedIngredient<I> ingredient,
                                               boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty()) {
            return List.of(); // fluido ou outro tipo: sem alvos
        }
        List<Target<I>> targets = new ArrayList<>();
        for (Slot slot : screen.visibleFilterSlots()) {
            Rect2i area = new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
            targets.add(new SlotTarget<>(area, screen.getMenu().containerId, slot.index, stack.get()));
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    /** Um ghost slot como alvo de soltura. */
    private record SlotTarget<I>(Rect2i area, int containerId, int slot, ItemStack stack) implements Target<I> {

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            PacketDistributor.sendToServer(new FilterSlotPayload(containerId, slot, stack.copyWithCount(1)));
        }
    }
}
