package org.tinycore.colonybridge.integration.ae2;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Índice "item → receitas de bancada que o produzem", usado pelo {@link PatternEncoding}. Ler todas as receitas do
 * {@code RecipeManager} custa caro num modpack grande (o ATM10 tem dezenas de milhares), então o índice é montado uma
 * vez e reaproveitado até as receitas mudarem.
 * <p>
 * Invalidação: {@code /reload} dispara o {@link OnDatapackSyncEvent} sem jogador (o mesmo evento com jogador é só um
 * login, que não muda receitas); parar o servidor também limpa (singleplayer pode abrir outro mundo com outros
 * datapacks). Só na thread do servidor.
 */
public final class CraftingRecipeIndex {

    private static @Nullable Map<Item, List<RecipeHolder<CraftingRecipe>>> byResult;

    private CraftingRecipeIndex() {}

    /** Receitas de bancada cujo resultado é {@code item} (lista vazia se nenhuma). */
    public static List<RecipeHolder<CraftingRecipe>> recipesFor(Level level, Item item) {
        if (byResult == null) {
            byResult = build(level.getRecipeManager(), level);
        }
        return byResult.getOrDefault(item, List.of());
    }

    private static Map<Item, List<RecipeHolder<CraftingRecipe>>> build(RecipeManager manager, Level level) {
        Map<Item, List<RecipeHolder<CraftingRecipe>>> map = new HashMap<>();
        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) {
                continue; // receitas "de código" (fogos, mapas, tingir armadura): resultado depende da entrada
            }
            Item result;
            try {
                result = recipe.getResultItem(level.registryAccess()).getItem();
            } catch (RuntimeException e) {
                continue; // receita de mod que não sabe dizer o resultado sem a entrada
            }
            map.computeIfAbsent(result, k -> new ArrayList<>()).add(holder);
        }
        return map;
    }

    /** {@code /reload}: as receitas podem ter mudado. */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            byResult = null;
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        byResult = null;
    }
}
