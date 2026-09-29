package org.tinycore.colonybridge.logic.warehouse;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * Foto do conteúdo dos racks do armazém, tirada <b>uma vez por ciclo</b> da ponte. Antes, cada pedido
 * relia todos os slots de todos os racks; com a foto, cada pedido só percorre os <i>tipos</i> de item
 * guardados (bem menos que slots), e as entregas feitas no próprio ciclo são somadas com {@link #add}.
 * <p>
 * Usa o {@code KeyCounter} do AE2 (mapa item → quantidade que já considera componentes, como
 * encantamentos) só como estrutura de dados; nada aqui mexe na rede ME.
 */
public final class WarehouseSnapshot {

    private final KeyCounter counts = new KeyCounter();

    private WarehouseSnapshot() {}

    /** Lê todos os slots dos racks uma vez. */
    public static WarehouseSnapshot of(List<IItemHandler> racks) {
        WarehouseSnapshot snapshot = new WarehouseSnapshot();
        for (IItemHandler rack : racks) {
            for (int slot = 0; slot < rack.getSlots(); slot++) {
                ItemStack stack = rack.getStackInSlot(slot);
                AEItemKey key = stack.isEmpty() ? null : AEItemKey.of(stack);
                if (key != null) {
                    snapshot.counts.add(key, stack.getCount());
                }
            }
        }
        return snapshot;
    }

    /**
     * Quanto existe de itens aceitos pelo filtro. O stack passado ao filtro é o da própria chave do AE2
     * (em cache, sem alocação): só pode ser lido.
     */
    public long count(Predicate<ItemStack> filter) {
        long total = 0;
        for (Object2LongMap.Entry<AEKey> entry : counts) {
            if (entry.getKey() instanceof AEItemKey key && filter.test(key.getReadOnlyStack())) {
                total += entry.getLongValue();
            }
        }
        return total;
    }

    /** Registra itens colocados nos racks durante o ciclo (a foto não é relida). */
    public void add(AEItemKey key, long amount) {
        counts.add(key, amount);
    }
}
