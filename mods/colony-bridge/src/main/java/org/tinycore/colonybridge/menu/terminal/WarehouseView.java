package org.tinycore.colonybridge.menu.terminal;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.logic.warehouse.WarehouseItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado cliente da grade do Terminal do Armazém: a cópia local do conteúdo do armazém, montada a partir
 * das mudanças que o servidor manda ({@code WarehouseContentsPayload}).
 * <p>
 * Só guarda dados (nenhuma classe de tela), por isso mora no pacote do menu. A tela consulta
 * {@link #version()} para saber quando refazer a busca/ordenação, em vez de refazer a cada frame.
 */
public final class WarehouseView {

    private final Object2LongLinkedOpenCustomHashMap<ItemStack> counts = WarehouseItems.newCountMap();
    private int version;

    /** Aplica um pacote do servidor. {@code reset} apaga o que havia antes. */
    public void apply(boolean reset, List<WarehouseEntry> entries) {
        if (reset) {
            counts.clear();
        }
        for (WarehouseEntry entry : entries) {
            if (entry.item().isEmpty()) {
                continue; // não deveria vir do servidor; ignorado
            }
            if (entry.count() <= 0) {
                counts.removeLong(entry.item());
            } else {
                counts.put(entry.item(), entry.count());
            }
        }
        version++;
    }

    /** Muda a cada {@link #apply}; a tela compara com o último valor visto. */
    public int version() {
        return version;
    }

    /** Todas as entradas, na ordem em que chegaram (a tela filtra e ordena). */
    public List<WarehouseEntry> entries() {
        List<WarehouseEntry> list = new ArrayList<>(counts.size());
        for (var entry : counts.object2LongEntrySet()) {
            list.add(new WarehouseEntry(entry.getKey(), entry.getLongValue()));
        }
        return list;
    }

    public long count(ItemStack item) {
        return counts.getLong(item);
    }
}
