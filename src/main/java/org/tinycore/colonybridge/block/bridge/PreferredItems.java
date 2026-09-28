package org.tinycore.colonybridge.block.bridge;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Itens preferidos da ponte para o modo "Lista" do craft por tag: {@link #SIZE} ghost slots, e a ordem
 * dos slots é a ordem de preferência. Como o {@link ItemFilter}, guarda só modelos (1 unidade), que
 * nunca são entregues nem voltam ao jogador. A tela edita pela aba "Preferidos".
 */
public final class PreferredItems {

    public static final int SIZE = 18;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    /** Lista viva (não uma cópia): o container dos ghost slots lê e escreve direto nela. */
    public NonNullList<ItemStack> items() {
        return items;
    }

    /** Ids dos itens na ordem dos slots, pulando os vazios (ex.: "minecraft:stone_pickaxe"). */
    public List<String> ids() {
        List<String> ids = new ArrayList<>(SIZE);
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        return ids;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        return ContainerHelper.saveAllItems(new CompoundTag(), items, registries);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.replaceAll(ignored -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
