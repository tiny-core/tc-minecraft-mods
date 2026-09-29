package org.tinycore.colonybridge.block.supply;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

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

    public static final int KEEP_SLOTS = 18;
    public static final int SURPLUS_SLOTS = 18;
    public static final int SIZE = KEEP_SLOTS + SURPLUS_SLOTS;
    /** Slots por seção na versão 1 do formato (9 + 9), para migrar blocos antigos. */
    static final int OLD_KEEP_SLOTS = 9;
    static final int OLD_SIZE = 18;
    /** Versão do formato salvo; 2 = 18 + 18. Sem a chave = versão 1. */
    private static final int LAYOUT = 2;

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
        tag.putInt("layout", LAYOUT);
        return tag;
    }

    /**
     * Lê do NBT. Blocos salvos no formato antigo (9 + 9, sem a chave {@code layout}) têm as linhas de
     * excedente movidas para a posição nova ({@link #migratedIndex}), senão virariam linhas "manter".
     */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        int[] saved = tag.getIntArray("amounts");
        boolean old = tag.getInt("layout") < LAYOUT;
        Arrays.fill(amounts, 0);
        if (old) {
            for (int i = OLD_SIZE - 1; i >= OLD_KEEP_SLOTS; i--) { // de trás para frente: não sobrescreve
                items.set(migratedIndex(i), items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < saved.length; i++) {
            int target = old ? migratedIndex(i) : i;
            if (target >= 0 && target < SIZE) {
                amounts[target] = Math.max(0, Math.min(MAX_AMOUNT, saved[i]));
            }
        }
    }

    /** Posição nova de uma linha do formato antigo: "manter" fica; "excedente" (9 a 17) vai para 18 a 26. */
    static int migratedIndex(int oldIndex) {
        return oldIndex < OLD_KEEP_SLOTS ? oldIndex : oldIndex - OLD_KEEP_SLOTS + KEEP_SLOTS;
    }
}
