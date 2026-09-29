package org.tinycore.core.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Container dos ghost slots. No servidor envolve as listas vivas do bloco (ex.: filtro e itens preferidos
 * da ponte, juntos por {@link JoinedList}), então dois jogadores com a tela aberta veem a mesma coisa; no
 * cliente, uma lista local que o Minecraft preenche com a sincronização normal de slots.
 * <p>
 * {@code removeItem} nunca devolve item: nada sai daqui para o jogador (proteção contra duplicação).
 */
final class GhostContainer implements Container {

    private final List<ItemStack> items;
    private final Runnable onChanged;

    GhostContainer(List<ItemStack> items, Runnable onChanged) {
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
        return true; // a distância é validada pelo menu (stillValid)
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
