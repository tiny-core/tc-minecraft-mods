package org.tinycore.colonybridge.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.logic.encoder.RecipeRanking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Receita de bancada → padrão de crafting do AE2, para o TC Pattern Encoder. Único lugar do mod que monta padrões.
 * <ul>
 *   <li>{@link #find}: acha a receita mais barata ({@link RecipeRanking}) que produz exatamente o item pedido e
 *       monta a grade 3×3 com um item de cada ingrediente (o que a rede ME tem mais, senão o primeiro);</li>
 *   <li>{@link #encode}: confere de novo a receita com essa grade ({@code matches} + resultado igual) e grava o
 *       padrão pela API pública do AE2 ({@code PatternDetailsHelper.encodeCraftingPattern}), com substituição de
 *       ingredientes ligada (o AE2 aceita qualquer item do ingrediente, como o botão do Pattern Encoding Terminal).</li>
 * </ul>
 * Fora: itens do Domum Ornamentum (blocos da bancada do arquiteto, com textura de material no item) e receitas
 * especiais. O item tem que sair da receita <b>igual</b> ao pedido (componentes inclusos): ferramenta encantada,
 * poção etc. ficam "sem receita".
 */
public final class PatternEncoding {

    /** Mods cujos itens não viram padrão (resultado depende de componentes que a bancada comum não dá). */
    private static final Set<String> EXCLUDED_NAMESPACES = Set.of("domum_ornamentum");
    private static final ResourceLocation BLANK_PATTERN = ResourceLocation.fromNamespaceAndPath("ae2", "blank_pattern");
    private static final int GRID = 3;

    private PatternEncoding() {}

    /** Receita escolhida e a grade montada, prontas para {@link #encode}. */
    public record Encodable(RecipeHolder<CraftingRecipe> recipe, ItemStack[] inputs, ItemStack output) {

        /** Ingredientes somados por item (para a tela), sem os espaços vazios. */
        public List<ItemStack> summedInputs() {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack input : inputs) {
                if (input.isEmpty()) continue;
                ItemStack same = result.stream().filter(s -> ItemStack.isSameItemSameComponents(s, input)).findFirst().orElse(null);
                if (same != null) same.grow(1);
                else result.add(input.copyWithCount(1));
            }
            return result;
        }
    }

    /** Situação de um item quanto a virar padrão. */
    public enum Lookup { FOUND, NO_RECIPE, EXCLUDED }

    public record Result(Lookup lookup, @Nullable Encodable encodable) {}

    /** true se o item não pode virar padrão por regra do mod (namespace excluído). */
    public static boolean isExcluded(ItemStack wanted) {
        return EXCLUDED_NAMESPACES.contains(BuiltInRegistries.ITEM.getKey(wanted.getItem()).getNamespace());
    }

    /**
     * Procura a melhor receita de bancada que produz {@code wanted} (mesmo item e componentes).
     *
     * @param stock    estoque da rede ME (cache do AE2), para escolher ingredientes e ranquear receitas
     * @param crafting padrões que a rede já tem (ingrediente craftável conta como disponível)
     */
    public static Result find(Level level, ItemStack wanted, KeyCounter stock, ICraftingService crafting) {
        if (isExcluded(wanted)) {
            return new Result(Lookup.EXCLUDED, null);
        }
        List<Scored> candidates = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : CraftingRecipeIndex.recipesFor(level, wanted.getItem())) {
            if (EXCLUDED_NAMESPACES.contains(holder.id().getNamespace())) continue;
            Encodable e = build(level, holder, wanted, stock);
            if (e != null) {
                candidates.add(new Scored(e, countMissing(holder.value().getIngredients(), stock, crafting)));
            }
        }
        int best = RecipeRanking.best(candidates, Scored::missing,
                c -> (int) Arrays.stream(c.encodable().inputs()).filter(s -> !s.isEmpty()).count(),
                c -> c.encodable().output().getCount());
        return best < 0 ? new Result(Lookup.NO_RECIPE, null) : new Result(Lookup.FOUND, candidates.get(best).encodable());
    }

    /** Candidata e quantos ingredientes dela a rede não tem nem sabe craftar. */
    private record Scored(Encodable encodable, int missing) {}

    /** Monta a grade da receita e confere se ela produz o item pedido; null se não serve. */
    private static @Nullable Encodable build(Level level, RecipeHolder<CraftingRecipe> holder, ItemStack wanted,
                                             KeyCounter stock) {
        CraftingRecipe recipe = holder.value();
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        ItemStack[] grid = new ItemStack[GRID * GRID];
        Arrays.fill(grid, ItemStack.EMPTY);
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : GRID;
        if (ingredients.size() > GRID * GRID || width > GRID) {
            return null;
        }
        for (int i = 0; i < ingredients.size(); i++) {
            int slot = (i / width) * GRID + (i % width); // forma: linha × coluna; sem forma: em sequência
            grid[slot] = pick(ingredients.get(i), stock);
        }
        try {
            CraftingInput input = CraftingInput.of(GRID, GRID, Arrays.asList(grid));
            if (!recipe.matches(input, level)) {
                return null;
            }
            ItemStack output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty() || !ItemStack.isSameItemSameComponents(output, wanted)) {
                return null;
            }
            return new Encodable(holder, grid, output);
        } catch (RuntimeException e) {
            ColonyBridgeMod.LOG.debug("Receita {} ignorada pelo Pattern Encoder: {}", holder.id(), e.toString());
            return null;
        }
    }

    /** Opção do ingrediente que a rede ME tem mais; sem estoque de nenhuma, a primeira. */
    private static ItemStack pick(Ingredient ingredient, KeyCounter stock) {
        ItemStack[] options = ingredient.getItems();
        if (ingredient.isEmpty() || options.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack best = options[0];
        long bestAmount = -1;
        for (ItemStack option : options) {
            AEItemKey key = AEItemKey.of(option);
            long amount = key == null ? 0 : stock.get(key);
            if (amount > bestAmount) {
                best = option;
                bestAmount = amount;
            }
        }
        return best.copyWithCount(1);
    }

    private static int countMissing(List<Ingredient> ingredients, KeyCounter stock, ICraftingService crafting) {
        int missing = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            boolean available = false;
            for (ItemStack option : ingredient.getItems()) {
                AEItemKey key = AEItemKey.of(option);
                if (key != null && (stock.get(key) > 0 || crafting.isCraftable(key))) {
                    available = true;
                    break;
                }
            }
            if (!available) missing++;
        }
        return missing;
    }

    /**
     * Grava o padrão. Confere a receita de novo (pode ter mudado com {@code /reload}); null se não deu, sem efeito
     * colateral (o Blank Pattern só é gasto por quem chama, depois de receber o padrão).
     */
    public static @Nullable ItemStack encode(Level level, Encodable e) {
        try {
            CraftingInput input = CraftingInput.of(GRID, GRID, Arrays.asList(e.inputs()));
            if (!e.recipe().value().matches(input, level)) {
                return null;
            }
            ItemStack pattern = PatternDetailsHelper.encodeCraftingPattern(e.recipe(), e.inputs(), e.output(), true, false);
            return PatternDetailsHelper.decodePattern(pattern, level) != null ? pattern : null;
        } catch (RuntimeException ex) {
            ColonyBridgeMod.LOG.warn("Pattern Encoder: o AE2 recusou o padrão de {}: {}", e.recipe().id(), ex.toString());
            return null;
        }
    }

    /** true se é o Blank Pattern do AE2 (pelo id: o mod compila só contra a API, sem as definições internas). */
    public static boolean isBlankPattern(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BLANK_PATTERN);
    }

    /**
     * Tira 1 Blank Pattern da rede ME (gastando a energia da operação, como o AE2). Com {@code mode = SIMULATE}
     * só confere se dá.
     *
     * @return true se tirou (ou tiraria) 1
     */
    public static boolean takeBlankFromNetwork(IGrid grid, IActionSource source, Actionable mode) {
        AEItemKey blank = AEItemKey.of(BuiltInRegistries.ITEM.get(BLANK_PATTERN));
        if (blank == null) {
            return false;
        }
        return StorageHelper.poweredExtraction(grid.getEnergyService(), grid.getStorageService().getInventory(),
                blank, 1, source, mode) == 1;
    }

    /** Item principal que um padrão codificado produz, ou null (não é padrão / não decodifica). */
    public static @Nullable AEItemKey patternOutput(ItemStack stack, Level level) {
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return null;
        }
        try {
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
            GenericStack output = details == null ? null : details.getPrimaryOutput();
            return output != null && output.what() instanceof AEItemKey key ? key : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
