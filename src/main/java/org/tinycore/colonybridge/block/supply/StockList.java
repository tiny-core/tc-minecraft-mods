package org.tinycore.colonybridge.block.supply;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/**
 * As duas listas do bloco de abastecimento, guardadas num vetor só de {@link #SIZE} posições:
 * <ul>
 *   <li>0 a {@link #KEEP_SLOTS}-1 — <b>manter no armazém</b>: se houver menos que a quantidade alvo,
 *       o bloco tira da rede ME e coloca nos racks;</li>
 *   <li>{@link #KEEP_SLOTS} até o fim — <b>excedente para o ME</b>: o que passar da quantidade alvo
 *       sai dos racks e vai para a rede.</li>
 * </ul>
 * A posição decide o modo, então não é preciso guardar um "tipo" por linha. Os itens são só modelos
 * de comparação (ghost): nunca são entregues a ninguém. A quantidade alvo de cada linha fica em
 * {@link #amounts}, e não na pilha do item, para não esbarrar no limite de tamanho de pilha.
 */
public final class StockList {

    public static final int KEEP_SLOTS = 9;
    public static final int SURPLUS_SLOTS = 9;
    public static final int SIZE = KEEP_SLOTS + SURPLUS_SLOTS;

    /** Quantidade alvo padrão ao colocar um item numa linha vazia. */
    public static final int DEFAULT_AMOUNT = 64;
    public static final int MAX_AMOUNT = 9999;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final int[] amounts = new int[SIZE];

    /** Lista viva (não uma cópia): o container dos ghost slots lê e escreve direto nela. */
    public NonNullList<ItemStack> items() {
        return items;
    }

    public ItemStack item(int slot) {
        return items.get(slot);
    }

    public int amount(int slot) {
        return amounts[slot];
    }

    /** Guarda a quantidade alvo já limitada (o valor vem da tela, então nunca é confiado). */
    public void setAmount(int slot, int amount) {
        if (slot >= 0 && slot < SIZE) {
            amounts[slot] = Math.max(0, Math.min(MAX_AMOUNT, amount));
        }
    }

    /** true se esta posição é do modo "manter no armazém". */
    public static boolean isKeep(int slot) {
        return slot < KEEP_SLOTS;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
        tag.putIntArray("amounts", amounts);
        return tag;
    }

    /** Lê do NBT; um vetor de quantidades de tamanho diferente (versão antiga) é ignorado. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        int[] saved = tag.getIntArray("amounts");
        for (int i = 0; i < SIZE; i++) {
            amounts[i] = i < saved.length ? Math.max(0, Math.min(MAX_AMOUNT, saved[i])) : 0;
        }
    }
}
