package org.tinycore.colonybridge.client.jei;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;
import org.tinycore.colonybridge.network.TerminalRecipePayload;
import org.tinycore.colonybridge.registry.ModMenus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Botão "+" do JEI numa receita de bancada com o Terminal do Armazém aberto: monta a receita na grade 3×3
 * com itens do armazém (e do inventário, se faltar no armazém), como no Crafting Terminal do AE2.
 * <p>
 * Antes de mandar, confere no cliente se cada ingrediente existe no armazém ({@code WarehouseView}) ou no
 * inventário; se faltar algum, o JEI mostra o "+" vermelho e marca as posições que faltam. O servidor
 * confere tudo de novo ao receber ({@code TerminalCrafting.fillFromRecipe}).
 */
final class TerminalRecipeTransfer
        implements IRecipeTransferHandler<WarehouseTerminalMenu, RecipeHolder<CraftingRecipe>> {

    private final IRecipeTransferHandlerHelper helper;

    TerminalRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends WarehouseTerminalMenu> getContainerClass() {
        return WarehouseTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<WarehouseTerminalMenu>> getMenuType() {
        return Optional.of(ModMenus.WAREHOUSE_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    /**
     * Chamado pelo JEI ao desenhar o "+" ({@code doTransfer = false}, só checa) e ao clicar nele
     * ({@code doTransfer = true}). O contexto traz menu, receita, slots, jogador e "Shift segurado" (max).
     */
    @Override
    public @Nullable IRecipeTransferError transferRecipe(
            IRecipeTransferContext<RecipeHolder<CraftingRecipe>, WarehouseTerminalMenu> context, boolean doTransfer) {
        return transfer(context.getContainer(), context.getRecipe(), context.getRecipeSlots(), context.getPlayer(),
                context.isMaxTransfer(), doTransfer);
    }

    /**
     * Forma antiga, que o JEI marcou para remoção mas ainda exige (é abstrata na interface). Só repassa; o JEI atual
     * chama a forma com contexto acima. Quando o JEI removê-la, basta apagar este método.
     */
    @Deprecated
    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(WarehouseTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe,
                                                         IRecipeSlotsView slots, Player player,
                                                         boolean maxTransfer, boolean doTransfer) {
        return transfer(menu, recipe, slots, player, maxTransfer, doTransfer);
    }

    private @Nullable IRecipeTransferError transfer(WarehouseTerminalMenu menu, RecipeHolder<CraftingRecipe> recipe,
                                                    IRecipeSlotsView slots, Player player,
                                                    boolean maxTransfer, boolean doTransfer) {
        // Posição de cada ingrediente na grade 3×3 (o JEI já resolve receitas menores que 3×3).
        Map<Integer, Ingredient> byGridSlot = helper.getGuiSlotIndexToIngredientMap(recipe);
        List<List<ItemStack>> options = new ArrayList<>();
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> missing = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = byGridSlot.get(i);
            List<ItemStack> choices = ingredient == null ? List.of() : limited(ingredient.getItems());
            options.add(choices);
            if (!choices.isEmpty() && !available(menu, player, choices) && i < inputs.size()) {
                missing.add(inputs.get(i));
            }
        }
        if (!missing.isEmpty()) {
            return helper.createUserErrorForMissingSlots(
                    Component.translatable("gui.tccolonybridge.terminal.missing"), missing);
        }
        if (doTransfer) {
            PacketDistributor.sendToServer(new TerminalRecipePayload(menu.containerId, options, maxTransfer));
        }
        return null;
    }

    /** Até o limite do pacote; cópias de 1 unidade (a quantidade é decidida no servidor). */
    private static List<ItemStack> limited(ItemStack[] items) {
        return Arrays.stream(items)
                .filter(stack -> !stack.isEmpty())
                .limit(TerminalRecipePayload.MAX_OPTIONS)
                .map(stack -> stack.copyWithCount(1))
                .toList();
    }

    /**
     * Alguma das opções existe no armazém (última atualização recebida), no inventário ou já na grade da
     * bancada (o servidor devolve a grade ao armazém antes de montar)?
     */
    private static boolean available(WarehouseTerminalMenu menu, Player player, List<ItemStack> choices) {
        for (ItemStack choice : choices) {
            if (menu.getView().count(choice) > 0) {
                return true;
            }
            for (ItemStack inSlot : player.getInventory().items) {
                if (ItemStack.isSameItemSameComponents(choice, inSlot)) {
                    return true;
                }
            }
            for (int slot = 0; slot < 9; slot++) {
                if (ItemStack.isSameItemSameComponents(choice, menu.getSlot(slot).getItem())) {
                    return true;
                }
            }
        }
        return false;
    }
}
