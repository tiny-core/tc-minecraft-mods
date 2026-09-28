package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.bridge.CycleReport;
import org.tinycore.colonybridge.stats.StatsSummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * O que a tela do monitor mostra: situação da ligação, estado da ponte, colônia, pedidos em aberto,
 * resumo de estatísticas e a lista de pedidos ({@link MonitorLine}, até {@link CycleReport#MAX_LINES}). Montado no servidor pelo mestre a cada segundo e enviado ao cliente só quando
 * muda (records comparam por valor, então {@code equals} decide).
 * <p>
 * Viaja no NBT de sincronização do block entity ({@link #save}/{@link #load}), mas não é salvo no
 * disco: é sempre recalculado a partir da ponte.
 */
public record MonitorData(LinkState link, BridgeStatus status, String colonyName, int openRequests,
                          StatsSummary stats, List<MonitorLine> requests) {

    /** Situação da ligação monitor → ponte. */
    public enum LinkState {
        /** Nenhuma ponte ligada (use o cartão). */
        UNLINKED,
        /** Ligado, mas a ponte não existe mais ou está num chunk descarregado. */
        BRIDGE_MISSING,
        /** Ligado e com dados. */
        OK
    }

    public static final MonitorData UNLINKED =
            new MonitorData(LinkState.UNLINKED, BridgeStatus.STARTING, "", 0, StatsSummary.EMPTY, List.of());
    public static final MonitorData MISSING =
            new MonitorData(LinkState.BRIDGE_MISSING, BridgeStatus.STARTING, "", 0, StatsSummary.EMPTY, List.of());

    private static final LinkState[] LINKS = LinkState.values();
    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    /** {@code registries}: necessário para gravar os textos (descrição dos pedidos). */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("link", link.ordinal());
        tag.putInt("status", status.ordinal());
        tag.putString("colony", colonyName);
        tag.putInt("open", openRequests);
        StatsSummary.Totals t = stats.totals();
        tag.putLongArray("totals", new long[]{t.itemsLastHour(), t.requestsLastHour(), t.itemsWindow(),
                t.requestsWindow(), t.craftsStarted(), t.craftsFailed()});
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
        return tag;
    }

    /** Lê com limites (índices de enum, tamanho de listas): o dado vem da rede. */
    public static MonitorData load(CompoundTag tag, HolderLookup.Provider registries) {
        long[] t = Arrays.copyOf(tag.getLongArray("totals"), 6);
        StatsSummary.Totals totals = new StatsSummary.Totals(t[0], t[1], t[2], t[3], t[4], t[5]);
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
        String colony = tag.getString("colony");
        return new MonitorData(
                LINKS[Math.floorMod(tag.getInt("link"), LINKS.length)],
                STATUSES[Math.floorMod(tag.getInt("status"), STATUSES.length)],
                colony.length() > 64 ? colony.substring(0, 64) : colony,
                tag.getInt("open"),
                new StatsSummary(tag.getInt("hours"), totals, chart, List.copyOf(top)),
                List.copyOf(requests));
    }
}
