package org.tinycore.colonybridge.client.bridge;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.FilterMode;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;
import org.tinycore.colonybridge.logic.crafting.ModFilterMode;
import org.tinycore.colonybridge.menu.bridge.BridgeTab;

/**
 * Ícone (um item do vanilla) de cada aba e de cada valor das configurações, para os botões da barra
 * lateral e as abas, no estilo dos terminais do AE2. O texto de cada opção fica no tooltip do botão.
 * O ícone de redstone é compartilhado com o Abastecedor ({@code RedstoneIcons}).
 * <p>
 * Os {@code ItemStack} são criados uma vez (constantes) e só lidos: nada é alocado a cada frame.
 */
final class BridgeIcons {

    private static final ItemStack BOOK = new ItemStack(Items.BOOK);
    private static final ItemStack HOPPER = new ItemStack(Items.HOPPER);
    private static final ItemStack NETHER_STAR = new ItemStack(Items.NETHER_STAR);
    private static final ItemStack KNOWLEDGE_BOOK = new ItemStack(Items.KNOWLEDGE_BOOK);
    private static final ItemStack CRAFTING_TABLE = new ItemStack(Items.CRAFTING_TABLE);
    private static final ItemStack COMMAND_BLOCK = new ItemStack(Items.COMMAND_BLOCK);
    private static final ItemStack COBBLESTONE = new ItemStack(Items.COBBLESTONE);
    private static final ItemStack DIAMOND = new ItemStack(Items.DIAMOND);
    private static final ItemStack WRITABLE_BOOK = new ItemStack(Items.WRITABLE_BOOK);
    private static final ItemStack BARRIER = new ItemStack(Items.BARRIER);
    private static final ItemStack IRON_INGOT = new ItemStack(Items.IRON_INGOT);
    private static final ItemStack ENCHANTED_BOOK = new ItemStack(Items.ENCHANTED_BOOK);
    private static final ItemStack CHEST = new ItemStack(Items.CHEST);
    private static final ItemStack NAME_TAG = new ItemStack(Items.NAME_TAG);

    private BridgeIcons() {}

    static ItemStack tab(BridgeTab tab) {
        return switch (tab) {
            case GENERAL -> BOOK;
            case FILTER -> HOPPER;
            case PREFERRED -> NETHER_STAR;
            case MODS -> KNOWLEDGE_BOOK;
        };
    }

    static ItemStack crafting() {
        return CRAFTING_TABLE;
    }

    /** Bloco de comando = padrão do servidor; pedra = mais barato; diamante = mais caro; livro = lista. */
    static ItemStack preference(@Nullable CraftPreference preference) {
        if (preference == null) {
            return COMMAND_BLOCK;
        }
        return switch (preference) {
            case CHEAPEST -> COBBLESTONE;
            case MOST_EXPENSIVE -> DIAMOND;
            case LIST -> WRITABLE_BOOK;
        };
    }

    static ItemStack filterMode(FilterMode mode) {
        return mode == FilterMode.BLOCK ? BARRIER : HOPPER;
    }

    static ItemStack exactMatch(boolean exact) {
        return exact ? ENCHANTED_BOOK : IRON_INGOT;
    }

    static ItemStack modMode(ModFilterMode mode) {
        return switch (mode) {
            case ALL -> CHEST;
            case ONLY -> NAME_TAG;
            case EXCEPT -> BARRIER;
            case PREFER -> NETHER_STAR;
        };
    }
}
