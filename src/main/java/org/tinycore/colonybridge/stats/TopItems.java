package org.tinycore.colonybridge.stats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Itens mais entregues numa janela de tempo, com memória limitada.
 * <p>
 * A janela é dividida em {@link #GROUPS} grupos (ex.: 24 horas); cada grupo guarda no máximo
 * {@link #PER_GROUP} itens. Quando um grupo enche, o item com menor contagem sai. O resultado é
 * <b>aproximado</b> (um item raro numa hora movimentada pode ficar de fora), em troca de tamanho fixo
 * na memória e no NBT.
 */
final class TopItems {

    static final int GROUPS = 24;
    static final int PER_GROUP = 16;

    /** Item e quantidade (resultado do ranking). */
    record Entry(Item item, long count) {}

    private final List<Map<Item, Long>> groups = new ArrayList<>(GROUPS);
    private long current = -1;

    TopItems() {
        for (int i = 0; i < GROUPS; i++) {
            groups.add(new HashMap<>());
        }
    }

    void add(long group, Item item, long amount) {
        advance(group);
        Map<Item, Long> counts = groups.get(index(group));
        counts.merge(item, amount, Long::sum);
        if (counts.size() > PER_GROUP) {
            counts.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(smallest -> counts.remove(smallest.getKey()));
        }
    }

    /** Os {@code limit} itens com maior soma na janela inteira. */
    List<Entry> top(int limit, long nowGroup) {
        advance(nowGroup);
        Map<Item, Long> total = new HashMap<>();
        for (Map<Item, Long> counts : groups) {
            counts.forEach((item, count) -> total.merge(item, count, Long::sum));
        }
        return total.entrySet().stream()
                .sorted(Map.Entry.<Item, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .toList();
    }

    private void advance(long group) {
        if (group <= current) {
            return;
        }
        long steps = current < 0 ? GROUPS : Math.min(group - current, GROUPS);
        for (long i = 0; i < steps; i++) {
            groups.get(index(group - i)).clear();
        }
        current = group;
    }

    private static int index(long group) {
        return (int) Math.floorMod(group, (long) GROUPS);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("current", current);
        ListTag list = new ListTag();
        for (Map<Item, Long> counts : groups) {
            ListTag entries = new ListTag();
            counts.forEach((item, count) -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
                entry.putLong("n", count);
                entries.add(entry);
            });
            list.add(entries);
        }
        tag.put("groups", list);
        return tag;
    }

    /** Lê do NBT; itens que não existem mais (mod removido) são descartados. */
    void load(CompoundTag tag) {
        groups.forEach(Map::clear);
        current = tag.contains("current") ? tag.getLong("current") : -1;
        ListTag list = tag.getList("groups", Tag.TAG_LIST);
        for (int g = 0; g < Math.min(list.size(), GROUPS); g++) {
            ListTag entries = list.getList(g);
            for (int i = 0; i < Math.min(entries.size(), PER_GROUP); i++) {
                CompoundTag entry = entries.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
                if (id == null) {
                    continue;
                }
                long count = entry.getLong("n");
                int group = g;
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> groups.get(group).put(item, count));
            }
        }
    }
}
