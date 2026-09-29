package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.logic.bridge.CycleReport;
import org.tinycore.colonybridge.stats.StatsSummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * O que o monitor mostra de uma Ponte: pedidos em aberto, resumo de estatísticas e a lista de pedidos
 * ({@link MonitorLine}, até {@link CycleReport#MAX_LINES}).
 */
public record BridgeContent(int openRequests, StatsSummary stats, List<MonitorLine> requests)
        implements MonitorContent {

    public static final String KIND = "bridge";
    public static final BridgeContent EMPTY = new BridgeContent(0, StatsSummary.EMPTY, List.of());

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("open", openRequests);
        StatsSummary.Totals t = stats.totals();
        tag.putLongArray("totals", new long[]{t.itemsLastHour(), t.requestsLastHour(), t.itemsWindow(),
                t.requestsWindow(), t.craftsStarted(), t.craftsFailed(), t.craftsDone()});
        tag.putInt("hours", stats.windowHours());
        tag.putIntArray("chart", stats.chart().stream().mapToInt(Integer::intValue).toArray());
        ListTag top = new ListTag();
        for (StatsSummary.Top entry : stats.top()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", BuiltInRegistries.ITEM.getKey(entry.item()).toString());
            e.putLong("n", entry.count());
            top.add(e);
        }
        tag.put("top", top);
        ListTag lines = new ListTag();
        for (MonitorLine line : requests) {
            lines.add(line.save(registries));
        }
        tag.put("requests", lines);
    }

    /** Lê com limites (tamanho de listas): o dado vem da rede. */
    static BridgeContent load(CompoundTag tag, HolderLookup.Provider registries) {
        long[] t = Arrays.copyOf(tag.getLongArray("totals"), 7); // dados antigos (6 valores): o 7º fica 0
        StatsSummary.Totals totals = new StatsSummary.Totals(t[0], t[1], t[2], t[3], t[4], t[5], t[6]);
        int[] chartRaw = tag.getIntArray("chart");
        List<Integer> chart = Arrays.stream(chartRaw, 0, Math.min(chartRaw.length, StatsSummary.MAX_CHART))
                .boxed().toList();
        List<StatsSummary.Top> top = new ArrayList<>();
        ListTag list = tag.getList("top", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), StatsSummary.MAX_TOP); i++) {
            CompoundTag e = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(e.getString("id"));
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> top.add(new StatsSummary.Top(item, e.getLong("n"))));
            }
        }
        List<MonitorLine> requests = new ArrayList<>();
        ListTag lines = tag.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(lines.size(), CycleReport.MAX_LINES); i++) {
            requests.add(MonitorLine.load(lines.getCompound(i), registries));
        }
        return new BridgeContent(tag.getInt("open"),
                new StatsSummary(tag.getInt("hours"), totals, chart, List.copyOf(top)), List.copyOf(requests));
    }
}
