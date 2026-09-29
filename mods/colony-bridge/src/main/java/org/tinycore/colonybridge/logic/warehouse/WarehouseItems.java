package org.tinycore.colonybridge.logic.warehouse;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Operações do Terminal do Armazém sobre os racks, com {@link ItemStack} puro (sem tipos do AE2, porque o
 * terminal não usa rede ME): somar tudo por tipo, tirar um item e guardar um item.
 * <p>
 * "Mesmo tipo" = mesmo item e mesmos componentes (encantamentos, durabilidade, nome...). Para isso o mapa
 * usa a estratégia {@code ItemStackLinkedSet.TYPE_AND_TAG} do Minecraft: um {@code IEqualityComparer} de
 * C#, que compara stacks por tipo e componentes ignorando a quantidade.
 * <p>
 * Nada aqui duplica itens: a retirada simula no slot antes de valer, e o que não couber ao guardar volta
 * para quem chamou como sobra.
 */
public final class WarehouseItems {

    private WarehouseItems() {}

    /** Mapa tipo de item → quantidade, com a comparação por tipo e componentes. */
    public static Object2LongLinkedOpenCustomHashMap<ItemStack> newCountMap() {
        return new Object2LongLinkedOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
    }

    /**
     * Soma o conteúdo de todos os racks por tipo. As chaves são cópias de 1 unidade (os stacks dos racks
     * nunca saem daqui, para ninguém alterá-los por engano).
     */
    public static Object2LongLinkedOpenCustomHashMap<ItemStack> countAll(List<IItemHandler> racks) {
        Object2LongLinkedOpenCustomHashMap<ItemStack> counts = newCountMap();
        for (IItemHandler rack : racks) {
            for (int slot = 0; slot < rack.getSlots(); slot++) {
                ItemStack inSlot = rack.getStackInSlot(slot);
                if (inSlot.isEmpty()) {
                    continue;
                }
                long previous = counts.getLong(inSlot);
                if (previous == 0) {
                    counts.put(inSlot.copyWithCount(1), inSlot.getCount());
                } else {
                    counts.put(inSlot, previous + inSlot.getCount());
                }
            }
        }
        return counts;
    }

    /**
     * Tira até {@code max} itens iguais a {@code model} dos racks (limitado ao tamanho de um stack).
     *
     * @return o que saiu de fato (vazio se não havia nada)
     */
    public static ItemStack extract(List<IItemHandler> racks, ItemStack model, int max) {
        int limit = Math.min(max, model.getMaxStackSize());
        if (model.isEmpty() || limit <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        for (IItemHandler rack : racks) {
            for (int slot = 0; slot < rack.getSlots(); slot++) {
                int missing = limit - result.getCount();
                if (missing <= 0) {
                    return result;
                }
                ItemStack inSlot = rack.getStackInSlot(slot);
                if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(model, inSlot)) {
                    continue;
                }
                if (rack.extractItem(slot, missing, true).isEmpty()) {
                    continue; // slot recusou (ex.: travado): nem tenta de verdade
                }
                ItemStack taken = rack.extractItem(slot, missing, false);
                if (result.isEmpty()) {
                    result = taken;
                } else {
                    result.grow(taken.getCount());
                }
            }
        }
        return result;
    }

    /**
     * Guarda o stack nos racks, completando stacks existentes primeiro. Não altera o stack passado.
     *
     * @return o que não coube (vazio se coube tudo)
     */
    public static ItemStack insert(List<IItemHandler> racks, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (IItemHandler rack : racks) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = ItemHandlerHelper.insertItemStacked(rack, remaining, false);
        }
        return remaining;
    }
}
