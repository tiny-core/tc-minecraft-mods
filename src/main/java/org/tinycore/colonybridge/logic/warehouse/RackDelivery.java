package org.tinycore.colonybridge.logic.warehouse;

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

/**
 * Move itens da rede ME para os racks do armazém, sem risco de duplicar ou sumir com itens.
 * <p>
 * Garantia contra duplicação (ver {@code CLAUDE.md}, seção Segurança): primeiro calcula quanto cabe
 * nos racks, extrai da rede só essa quantidade ({@code SIMULATE} antes de {@code MODULATE}) e devolve à
 * rede o que por algum motivo não entrou.
 * <p>
 * Compartilhada pela Ponte ({@code BridgeLogic}) e pelo Abastecedor ({@code SupplyLogic}), para o ciclo não misturar "o que entregar" com "como mover".
 * {@code IItemHandler} é a interface de inventário do NeoForge (≈ uma interface de "slots" em C#);
 * os racks do MineColonies a expõem, então esta classe não depende do MineColonies.
 */
public final class RackDelivery {

    private RackDelivery() {}

    /**
     * Entrega até {@code wanted} itens de {@code key}.
     *
     * @return quantidade efetivamente colocada nos racks
     */
    public static long deliver(IGrid grid, IActionSource source, AEItemKey key, long wanted, List<IItemHandler> racks) {
        MEStorage inventory = grid.getStorageService().getInventory();
        IEnergySource energy = grid.getEnergyService();

        long available = StorageHelper.poweredExtraction(energy, inventory, key, wanted, source, Actionable.SIMULATE);
        if (available <= 0) {
            return 0;
        }
        long fits = capacity(racks, key, available);
        if (fits <= 0) {
            return 0; // racks cheios
        }
        long extracted = StorageHelper.poweredExtraction(energy, inventory, key, fits, source, Actionable.MODULATE);
        long leftover = insert(racks, key, extracted);
        if (leftover > 0) {
            long returned = StorageHelper.poweredInsert(energy, inventory, key, leftover, source);
            if (returned < leftover) {
                ColonyBridgeMod.LOG.warn("Não foi possível devolver {}x {} à rede ME", leftover - returned, key);
            }
        }
        return extracted - leftover;
    }

    /**
     * Quanto de {@code amount} cabe nos racks. Pergunta a cada slot, em simulação, quanto ele aceita de
     * um stack cheio e soma. Como cada slot é consultado uma única vez, a soma não conta o mesmo espaço
     * duas vezes (a versão antiga simulava stack por stack e superestimava).
     */
    private static long capacity(List<IItemHandler> racks, AEItemKey key, long amount) {
        ItemStack probe = key.toStack(key.getMaxStackSize()); // insertItem não altera o stack passado
        long total = 0;
        for (IItemHandler rack : racks) {
            for (int slot = 0; slot < rack.getSlots(); slot++) {
                total += probe.getCount() - rack.insertItem(slot, probe, true).getCount();
                if (total >= amount) {
                    return amount;
                }
            }
        }
        return total;
    }

    /** Insere de verdade, preferindo completar stacks existentes. @return quantidade que NÃO coube */
    private static long insert(List<IItemHandler> racks, AEItemKey key, long amount) {
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
                break; // não cabe mais nada
            }
        }
        return remaining;
    }
}
