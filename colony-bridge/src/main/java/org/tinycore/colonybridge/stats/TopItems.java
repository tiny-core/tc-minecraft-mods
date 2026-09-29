package org.tinycore.colonybridge.stats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Itens mais entregues numa janela de tempo, com memória limitada.
 * <p>
 * A regra do ranking (grupos de tempo, descarte do menor quando o grupo enche) está no
 * {@link TopRanking}, genérico e testado sem o jogo; aqui fica o que depende do Minecraft: a chave é
 * {@link Item} e o NBT grava o id do item. A janela tem {@link #GROUPS} grupos (ex.: 24 horas) de no
 * máximo {@link #PER_GROUP} itens.
 */
final class TopItems {

    static final int GROUPS = 24;
    static final int PER_GROUP = 16;

    /** Item e quantidade (resultado do ranking). */
    record Entry(Item item, long count) {}

    private final TopRanking<Item> ranking = new TopRanking<>(GROUPS, PER_GROUP);

    void add(long group, Item item, long amount) {
        ranking.add(group, item, amount);
    }

    /** Os {@code limit} itens com maior soma na janela inteira. */
    List<Entry> top(int limit, long nowGroup) {
        return ranking.top(limit, nowGroup).stream()
                .map(e -> new Entry(e.key(), e.count()))
                .toList();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("current", ranking.current());
        ListTag list = new ListTag();
        for (int g = 0; g < GROUPS; g++) {
            ListTag entries = new ListTag();
            ranking.group(g).forEach((item, count) -> {
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
        ranking.reset(tag.contains("current") ? tag.getLong("current") : -1);
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
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> ranking.group(group).put(item, count));
            }
        }
    }
}
