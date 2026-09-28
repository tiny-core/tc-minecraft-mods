package org.tinycore.colonybridge.logic;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.tinycore.colonybridge.ColonyBridgeMod;

import java.util.List;
import java.util.function.Predicate;

/**
 * Leitura e retirada de itens dos racks do armazém — o caminho oposto ao {@link RackDelivery}.
 * Usado pelo {@link SupplyLogic} para contar o estoque da colônia e mandar o excedente para a rede ME,
 * e pelo {@link BridgeLogic} para descontar do pedido o que o armazém já tem.
 * <p>
 * Garantia contra perda e duplicação: cada retirada é simulada nos dois lados (rack e rede) antes de
 * valer; o que a rede não aceitar volta imediatamente para os racks.
 */
final class WarehouseStock {

    private WarehouseStock() {}

    /** Quanto existe do item nos racks. Compara item e componentes (encantamento, durabilidade). */
    static long count(List<IItemHandler> racks, ItemStack model) {
        return count(racks, inSlot -> ItemStack.isSameItemSameComponents(model, inSlot));
    }

    /**
     * Quanto existe nos racks de itens aceitos pelo filtro. Usado pela ponte para saber quanto de um
     * pedido o armazém já tem. {@code Predicate<ItemStack>} ≈ {@code Func<ItemStack, bool>} em C#.
     * O stack passado ao filtro é o do próprio rack: só pode ser lido, nunca modificado.
     */
    static long count(List<IItemHandler> racks, Predicate<ItemStack> filter) {
        long total = 0;
        for (IItemHandler rack : racks) {
            for (int slot = 0; slot < rack.getSlots(); slot++) {
                ItemStack inSlot = rack.getStackInSlot(slot);
                if (!inSlot.isEmpty() && filter.test(inSlot)) {
                    total += inSlot.getCount();
                }
            }
        }
        return total;
    }

    /**
     * Tira até {@code max} itens dos racks e coloca na rede ME.
     *
     * @return quantidade que entrou de fato na rede
     */
    static long toNetwork(List<IItemHandler> racks, ItemStack model, long max, IGrid grid, IActionSource source) {
        AEItemKey key = AEItemKey.of(model);
        if (key == null || max <= 0) {
            return 0;
        }
        MEStorage inventory = grid.getStorageService().getInventory();
        IEnergySource energy = grid.getEnergyService();
        long moved = 0;
        for (IItemHandler rack : racks) {
            for (int slot = 0; slot < rack.getSlots() && moved < max; slot++) {
                ItemStack inSlot = rack.getStackInSlot(slot);
                if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(model, inSlot)) {
                    continue;
                }
                int wanted = (int) Math.min(max - moved, inSlot.getCount());
                ItemStack simulated = rack.extractItem(slot, wanted, true);
                if (simulated.isEmpty()) {
                    continue;
                }
                // Quanto a rede aceitaria: simulação sem gastar energia.
                long accepted = inventory.insert(key, simulated.getCount(), Actionable.SIMULATE, source);
                if (accepted <= 0) {
                    return moved; // rede cheia: não adianta continuar
                }
                ItemStack taken = rack.extractItem(slot, (int) accepted, false);
                if (taken.isEmpty()) {
                    continue;
                }
                long inserted = StorageHelper.poweredInsert(energy, inventory, key, taken.getCount(), source);
                moved += inserted;
                long leftover = taken.getCount() - inserted;
                if (leftover > 0) {
                    returnToRacks(racks, key, leftover);
                }
            }
        }
        return moved;
    }

    /** Devolve aos racks o que a rede não aceitou, para nada se perder. */
    private static void returnToRacks(List<IItemHandler> racks, AEItemKey key, long amount) {
        long remaining = amount;
        int maxStack = key.getMaxStackSize();
        while (remaining > 0) {
            int chunk = (int) Math.min(remaining, maxStack);
            ItemStack stack = key.toStack(chunk);
            for (IItemHandler rack : racks) {
                stack = ItemHandlerHelper.insertItemStacked(rack, stack, false);
                if (stack.isEmpty()) {
                    break;
                }
            }
            remaining -= chunk - stack.getCount();
            if (!stack.isEmpty()) {
                ColonyBridgeMod.LOG.warn("Não foi possível devolver {}x {} aos racks do armazém", remaining, key);
                return;
            }
        }
    }
}
