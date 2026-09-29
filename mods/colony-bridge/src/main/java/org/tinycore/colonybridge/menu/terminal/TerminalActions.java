package org.tinycore.colonybridge.menu.terminal;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.tinycore.colonybridge.logic.terminal.TerminalAction;
import org.tinycore.colonybridge.logic.warehouse.WarehouseItems;
import org.tinycore.colonybridge.logic.warehouse.WarehouseStock;

import java.util.List;

/**
 * Executa no servidor um clique na grade do Terminal do Armazém (tirar para o cursor, tirar para o
 * inventário, guardar o que está no cursor).
 * <p>
 * O cliente só mandou a ação e o tipo de item; quantidades vêm do que existe nos racks agora. Como a
 * retirada só encontra itens que já estão nos racks, um item "forjado" no pacote simplesmente não acha nada.
 * O que não couber no inventário volta ao armazém (ou, em último caso, cai no chão ao lado do jogador).
 * Cada ação devolve quantos itens moveu, para o menu cobrar a energia da rede.
 */
final class TerminalActions {

    private TerminalActions() {}

    /** @return quantos itens passaram entre o armazém e o jogador */
    static long apply(WarehouseTerminalMenu menu, ServerPlayer player, List<IItemHandler> racks,
                      TerminalAction action, ItemStack clicked) {
        ItemStack model = clicked.isEmpty() ? ItemStack.EMPTY : clicked.copyWithCount(1);
        return switch (action) {
            case TAKE_STACK -> takeToCursor(menu, racks, model, model.getMaxStackSize());
            case TAKE_HALF -> {
                long available = model.isEmpty() ? 0 : WarehouseStock.count(racks, model);
                int stack = (int) Math.min(available, model.getMaxStackSize());
                yield takeToCursor(menu, racks, model, (stack + 1) / 2);
            }
            case TAKE_TO_INVENTORY -> takeToInventory(player, racks, model);
            case INSERT_CARRIED -> insertCarried(menu, racks);
            case INSERT_ONE -> insertOne(menu, racks);
            case CLEAR_GRID, GRID_TO_INVENTORY -> 0; // tratados pelo menu, que é quem conhece a bancada
        };
    }

    /** Só com a mão vazia, como no AE2 (senão o item do cursor seria trocado/perdido). */
    private static long takeToCursor(WarehouseTerminalMenu menu, List<IItemHandler> racks, ItemStack model, int amount) {
        if (model.isEmpty() || !menu.getCarried().isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack taken = WarehouseItems.extract(racks, model, amount);
        menu.setCarried(taken);
        return taken.getCount();
    }

    private static long takeToInventory(ServerPlayer player, List<IItemHandler> racks, ItemStack model) {
        if (model.isEmpty()) {
            return 0;
        }
        ItemStack taken = WarehouseItems.extract(racks, model, model.getMaxStackSize());
        int count = taken.getCount();
        if (taken.isEmpty()) {
            return 0;
        }
        player.getInventory().add(taken); // "add" reduz o stack ao que não coube
        if (taken.isEmpty()) {
            return count;
        }
        count -= taken.getCount();
        ItemStack leftover = WarehouseItems.insert(racks, taken);
        if (!leftover.isEmpty()) {
            player.drop(leftover, false);
        }
        return count;
    }

    private static long insertCarried(WarehouseTerminalMenu menu, List<IItemHandler> racks) {
        ItemStack carried = menu.getCarried();
        ItemStack leftover = WarehouseItems.insert(racks, carried);
        menu.setCarried(leftover);
        return carried.getCount() - leftover.getCount();
    }

    private static long insertOne(WarehouseTerminalMenu menu, List<IItemHandler> racks) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || !WarehouseItems.insert(racks, carried.copyWithCount(1)).isEmpty()) {
            return 0;
        }
        carried.shrink(1);
        menu.setCarried(carried);
        return 1;
    }
}
