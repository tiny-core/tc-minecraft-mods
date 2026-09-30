package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.stats.StatsSummary;
import org.tinycore.colonybridge.stats.SupplySummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * O que o monitor mostra de um Abastecedor: estatísticas (trazido do ME / enviado ao ME), as linhas
 * configuradas ({@link StockLine}, até duas listas de {@link TargetList#HARD_MAX_LINES}) e há quanto tempo foi o último movimento.
 *
 * @param minutesSinceMove minutos desde o último movimento de qualquer linha; -1 = nenhum desde que o mundo carregou
 */
public record SupplyContent(SupplySummary stats, List<StockLine> lines, long minutesSinceMove) implements MonitorContent {

    public static final String KIND = "supply";

    @Override
    public String kind() {
        return KIND;
    }

    /** Linhas que precisam de atenção (falta na rede, armazém cheio, retido...). */
    public int needingAttention() {
        int count = 0;
        for (StockLine line : lines) {
            if (line.status().needsAttention()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("supply", new long[]{stats.restockedLastHour(), stats.returnedLastHour(),
                stats.restockedWindow(), stats.returnedWindow()});
        tag.putInt("hours", stats.windowHours());
        tag.putIntArray("chart", stats.chart().stream().mapToInt(Integer::intValue).toArray());
        ListTag list = new ListTag();
        for (StockLine line : lines) {
            list.add(line.save());
        }
        tag.put("stock", list);
        tag.putLong("lastMove", minutesSinceMove);
    }

    /** Lê com limites (tamanho de listas): o dado vem da rede. */
    static SupplyContent load(CompoundTag tag) {
        long[] s = Arrays.copyOf(tag.getLongArray("supply"), 4);
        int[] chartRaw = tag.getIntArray("chart");
        List<Integer> chart = Arrays.stream(chartRaw, 0, Math.min(chartRaw.length, StatsSummary.MAX_CHART))
                .boxed().toList();
        List<StockLine> lines = new ArrayList<>();
        ListTag list = tag.getList("stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(list.size(), TargetList.HARD_MAX_LINES * 2); i++) {
            StockLine line = StockLine.load(list.getCompound(i));
            if (line != null) {
                lines.add(line);
            }
        }
        return new SupplyContent(new SupplySummary(tag.getInt("hours"), s[0], s[1], s[2], s[3], chart),
                List.copyOf(lines), tag.contains("lastMove") ? tag.getLong("lastMove") : -1);
    }
}
