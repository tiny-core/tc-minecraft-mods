package org.tinycore.colonybridge.menu.terminal;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.logic.warehouse.WarehouseItems;

import java.util.List;
import java.util.Optional;

/**
 * A bancada 3×3 do Terminal do Armazém, como no Crafting Terminal do AE2: a grade, o resultado e o que a
 * diferencia de uma bancada comum — repor os ingredientes a partir do armazém depois de cada craft,
 * devolver a grade ao armazém e montar uma receita do JEI com itens do armazém.
 * <p>
 * {@code TransientCraftingContainer} é o inventário da grade da bancada vanilla; ele avisa o menu a cada
 * mudança ({@code slotsChanged}), e o menu chama {@link #updateResult} para recalcular a receita.
 */
final class TerminalCrafting {

    static final int SIZE = 9;

    private final WarehouseTerminalMenu menu;
    private final Player player;
    final TransientCraftingContainer grid;
    final ResultContainer result = new ResultContainer();

    TerminalCrafting(WarehouseTerminalMenu menu, Player player) {
        this.menu = menu;
        this.player = player;
        this.grid = new TransientCraftingContainer(menu, 3, 3);
    }

    /**
     * Recalcula o resultado (só no servidor) e o manda ao cliente — o mesmo que a bancada vanilla faz em
     * {@code CraftingMenu.slotChangedCraftingGrid}, que não pode ser chamado daqui (é protegido).
     */
    void updateResult(int resultSlotIndex) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Level level = serverPlayer.level();
        CraftingInput input = grid.asCraftInput();
        ItemStack output = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe =
                serverPlayer.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isPresent() && result.setRecipeUsed(level, serverPlayer, recipe.get())) {
            ItemStack assembled = recipe.get().value().assemble(input, level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                output = assembled;
            }
        }
        result.setItem(0, output);
        menu.setRemoteSlot(resultSlotIndex, output);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(),
                resultSlotIndex, output));
    }

    /** Cópia da grade, tirada antes de um craft para saber o que repor depois. */
    ItemStack[] snapshot() {
        ItemStack[] copy = new ItemStack[SIZE];
        for (int i = 0; i < SIZE; i++) {
            copy[i] = grid.getItem(i).copy();
        }
        return copy;
    }

    /**
     * Depois de um craft, completa cada slot da grade com o mesmo item tirado do armazém, até a quantidade
     * de antes. Slot onde ficou outro item (ex.: balde vazio que sobra da receita) não é mexido.
     */
    void refill(ItemStack[] before, List<IItemHandler> racks) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack was = before[i];
            ItemStack now = grid.getItem(i);
            if (was.isEmpty() || (!now.isEmpty() && !ItemStack.isSameItemSameComponents(now, was))) {
                continue;
            }
            int missing = was.getCount() - now.getCount();
            ItemStack got = missing > 0 ? WarehouseItems.extract(racks, was, missing) : ItemStack.EMPTY;
            if (got.isEmpty()) {
                continue;
            }
            got.grow(now.getCount());
            grid.setItem(i, got);
        }
    }

    /** Guarda no armazém tudo o que está na grade; o que não couber fica na grade. */
    void clearTo(List<IItemHandler> racks) {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = grid.getItem(i);
            if (!stack.isEmpty()) {
                grid.setItem(i, WarehouseItems.insert(racks, stack));
            }
        }
    }

    /**
     * Monta uma receita vinda do JEI: esvazia a grade (para o armazém) e, para cada posição, usa a primeira
     * opção que existir — primeiro no armazém, depois no inventário do jogador. {@code max} = um stack por
     * posição (Shift no "+" do JEI); senão, uma unidade.
     * <p>
     * As opções vieram do cliente, mas só servem de filtro: o que entra na grade é sempre um item que já
     * existia no armazém ou no inventário.
     */
    void fillFromRecipe(List<IItemHandler> racks, List<List<ItemStack>> options, boolean max) {
        clearTo(racks);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < SIZE; i++) {
            if (!grid.getItem(i).isEmpty()) {
                inventory.placeItemBackInInventory(grid.removeItemNoUpdate(i)); // armazém cheio: vai para o jogador
            }
        }
        for (int i = 0; i < Math.min(SIZE, options.size()); i++) {
            for (ItemStack option : options.get(i)) {
                ItemStack got = take(racks, inventory, option, max ? option.getMaxStackSize() : 1);
                if (!got.isEmpty()) {
                    grid.setItem(i, got);
                    break;
                }
            }
        }
    }

    private static ItemStack take(List<IItemHandler> racks, Inventory inventory, ItemStack option, int amount) {
        if (option.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack model = option.copyWithCount(1);
        ItemStack fromWarehouse = WarehouseItems.extract(racks, model, amount);
        if (!fromWarehouse.isEmpty()) {
            return fromWarehouse;
        }
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack inSlot = inventory.items.get(slot);
            if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(model, inSlot)) {
                return inventory.removeItem(slot, Math.min(amount, inSlot.getCount()));
            }
        }
        return ItemStack.EMPTY;
    }
}
