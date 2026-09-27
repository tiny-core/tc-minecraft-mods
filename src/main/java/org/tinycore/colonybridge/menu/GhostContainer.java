package org.tinycore.colonybridge.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Container dos ghost slots do filtro. No servidor envolve a lista viva do {@code ItemFilter} da ponte
 * (então dois jogadores com a tela aberta veem a mesma coisa); no cliente, uma lista local que o
 * Minecraft preenche com a sincronização normal de slots.
 * <p>
 * {@code removeItem} nunca devolve item: nada sai daqui para o jogador (proteção contra duplicação).
 */
final class GhostContainer implements Container {

    private final NonNullList<ItemStack> items;
    private final Runnable onChanged;

    GhostContainer(NonNullList<ItemStack> items, Runnable onChanged) {
        this.items = items;
        this.onChanged = onChanged;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    /** Ghost: não entrega nada. */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    /** Ghost: não entrega nada. */
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        setChanged();
    }

    @Override
    public void setChanged() {
        onChanged.run();
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // a distância é validada pelo menu (stillValid do ColonyBridgeMenu)
    }

    @Override
    public void clearContent() {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        setChanged();
    }
}
