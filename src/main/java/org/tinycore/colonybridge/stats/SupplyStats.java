package org.tinycore.colonybridge.stats;

import net.minecraft.nbt.CompoundTag;

/**
 * Estatísticas de um Abastecedor: quanto ele repôs no armazém e quanto devolveu à rede ME, na mesma
 * janela de tempo da Ponte ({@link MetricSeries}). Memória constante; salvo no NBT do block entity.
 */
public final class SupplyStats {

    private static final int CHART_BARS = 24;

    private final MetricSeries<SupplyMetric> series = new MetricSeries<>(SupplyMetric.class, ignored -> {});

    public void recordRestocked(long now, long amount) {
        if (amount > 0) {
            series.add(SupplyMetric.ITEMS_RESTOCKED, now, amount);
        }
    }

    public void recordReturned(long now, long amount) {
        if (amount > 0) {
            series.add(SupplyMetric.ITEMS_RETURNED, now, amount);
        }
    }

    /** true (uma vez) se houve registro desde a última chamada. */
    public boolean consumeDirty() {
        return series.consumeDirty();
    }

    public SupplySummary summary(long now) {
        return new SupplySummary(series.windowHours(),
                series.lastHour(SupplyMetric.ITEMS_RESTOCKED, now),
                series.lastHour(SupplyMetric.ITEMS_RETURNED, now),
                series.window(SupplyMetric.ITEMS_RESTOCKED, now),
                series.window(SupplyMetric.ITEMS_RETURNED, now),
                series.chart(SupplyMetric.ITEMS_RESTOCKED, CHART_BARS, now));
    }

    public CompoundTag save() {
        return series.save(tag -> {});
    }

    public void load(CompoundTag tag) {
        series.load(tag);
    }
}
