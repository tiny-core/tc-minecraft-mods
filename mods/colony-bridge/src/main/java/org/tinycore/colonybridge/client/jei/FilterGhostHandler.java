package org.tinycore.colonybridge.client.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.client.list.TargetListWidget;
import org.tinycore.colonybridge.network.FilterSlotPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Diz ao JEI onde um item arrastado pode ser solto e o que fazer ao soltar, nas telas com listas:
 * <ul>
 *   <li>ícones das linhas e o "+" de uma {@link TargetListWidget} (Abastecedor, filtro da Ponte) → a lista manda
 *       o pacote de edição;</li>
 *   <li>ghost slots visíveis (preferidos da Ponte) → {@link FilterSlotPayload}.</li>
 * </ul>
 * O servidor valida e grava só uma cópia de 1 unidade. {@code <I>} é um generic de método (≈
 * {@code Method<T>()} em C#): o JEI trabalha com vários tipos de ingrediente; aqui só aceitamos itens.
 *
 * @param <T> tipo da tela (cada tela registra o seu handler, com funções que acham a lista e os slots)
 */
final class FilterGhostHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {

    private final Function<T, TargetListWidget> list;
    private final Function<T, List<Slot>> ghostSlots;

    FilterGhostHandler(Function<T, TargetListWidget> list, Function<T, List<Slot>> ghostSlots) {
        this.list = list;
        this.ghostSlots = ghostSlots;
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        if (stack.isEmpty()) {
            return List.of(); // fluido ou outro tipo: sem alvos
        }
        List<Target<I>> targets = new ArrayList<>();
        TargetListWidget widget = list.apply(screen);
        for (TargetListWidget.DropTarget drop : widget.dropTargets()) {
            targets.add(new ListTarget<>(drop.area(), widget, drop.index(), stack.get()));
        }
        for (Slot slot : ghostSlots.apply(screen)) {
            Rect2i area = new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
            targets.add(new SlotTarget<>(area, screen.getMenu().containerId, slot.index, stack.get()));
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    /** Ícone de uma linha (ou o "+") como alvo de soltura. */
    private record ListTarget<I>(Rect2i area, TargetListWidget widget, int index, ItemStack stack)
            implements Target<I> {

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            widget.acceptDrop(index, stack);
        }
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
