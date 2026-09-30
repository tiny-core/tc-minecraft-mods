package org.tinycore.colonybridge.block.bridge;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.logic.target.TargetList;

/**
 * Lista de itens do filtro da ponte ({@link #SIZE} posições, cada uma com 1 item "fantasma" ou vazia).
 * <p>
 * Os itens aqui são só modelos para comparação: nunca são entregues nem voltam para o jogador.
 * A tela edita a lista pelos ghost slots ({@code menu/GhostSlot}); a lógica consulta via
 * {@code ColonyBridgeBlockEntity.filterAllows}.
 * {@code NonNullList} é uma lista do Minecraft que usa {@code ItemStack.EMPTY} no lugar de null.
 */
public final class ItemFilter {

    public static final int SIZE = 18;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    /** Lista viva (não uma cópia): o container dos ghost slots lê e escreve direto nela. */
    public NonNullList<ItemStack> items() {
        return items;
    }

    /**
     * true se o item está na lista.
     *
     * @param exact true = mesmo item e mesmos componentes (encantamentos, durabilidade...);
     *              false = só o tipo do item
     */
    public boolean contains(ItemStack stack, boolean exact) {
        for (ItemStack entry : items) {
            if (entry.isEmpty()) {
                continue;
            }
            if (exact ? ItemStack.isSameItemSameComponents(entry, stack) : stack.is(entry.getItem())) {
                return true;
            }
        }
        return false;
    }

    /** {@code ContainerHelper} é o utilitário do Minecraft que salva listas de itens em NBT (como baús). */
    public CompoundTag save(HolderLookup.Provider registries) {
        return ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
    }

    /** Migração para a lista de item/tag/mod (Fase 9): cada slot com item vira uma linha, na mesma ordem. */
    public void exportTo(TargetList filter) {
        filter.importSlots(items, null, 0, SIZE);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
