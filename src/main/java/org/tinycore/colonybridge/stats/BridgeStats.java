package org.tinycore.colonybridge.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Estatísticas de uma ponte: registra eventos (entregas, crafts) de forma incremental e monta o
 * {@link StatsSummary} para os monitores.
 * <p>
 * Memória constante: contadores numa {@link MetricSeries} (janela de tempo da config) e ranking em
 * {@link TopItems}. O tempo é o do jogo ({@code gameTime}): com o servidor parado, nada anda.
 * Se {@code statsBucketTicks} ou {@code statsBuckets} mudarem na config, as estatísticas são zeradas.
 */
public final class BridgeStats {

    private static final int CHART_BARS = 24;

    private TopItems top = new TopItems();
    private final MetricSeries<StatMetric> series = new MetricSeries<>(StatMetric.class, this::resetTop);

    public void recordDelivery(long now, Item item, long amount) {
        series.add(StatMetric.REQUESTS_DELIVERED, now, 1);
        series.add(StatMetric.ITEMS_DELIVERED, now, amount);
        top.add(topGroup(now), item, amount);
    }

    public void recordCraftStarted(long now) {
        series.add(StatMetric.CRAFTS_STARTED, now, 1);
    }

    /**
     * Itens de um craft colocados direto no armazém. Conta itens e ranking, mas não "pedido atendido":
     * um craft chega em várias partes e o pedido é contado quando a ponte o reatribui.
     */
    public void recordCraftDelivery(long now, Item item, long amount) {
        series.add(StatMetric.ITEMS_DELIVERED, now, amount);
        top.add(topGroup(now), item, amount);
    }

    /** Um job de craft desta ponte terminou no AE2. */
    public void recordCraftDone(long now) {
        series.add(StatMetric.CRAFTS_DONE, now, 1);
    }

    public void recordCraftFailed(long now) {
        series.add(StatMetric.CRAFTS_FAILED, now, 1);
    }

    /** true (uma vez) se houve registro desde a última chamada. */
    public boolean consumeDirty() {
        return series.consumeDirty();
    }

    public StatsSummary summary(long now) {
        StatsSummary.Totals totals = new StatsSummary.Totals(
                series.lastHour(StatMetric.ITEMS_DELIVERED, now),
                series.lastHour(StatMetric.REQUESTS_DELIVERED, now),
                series.window(StatMetric.ITEMS_DELIVERED, now),
                series.window(StatMetric.REQUESTS_DELIVERED, now),
                series.window(StatMetric.CRAFTS_STARTED, now),
                series.window(StatMetric.CRAFTS_FAILED, now),
                series.window(StatMetric.CRAFTS_DONE, now));
        List<StatsSummary.Top> ranking = top.top(StatsSummary.MAX_TOP, topGroup(now)).stream()
                .map(e -> new StatsSummary.Top(e.item(), e.count()))
                .toList();
        return new StatsSummary(series.windowHours(), totals,
                series.chart(StatMetric.ITEMS_DELIVERED, CHART_BARS, now), ranking);
    }

    /** Cada grupo do ranking cobre 1/{@link TopItems#GROUPS} da janela (1 hora com a config padrão). */
    private long topGroup(long now) {
        long groupTicks = Math.max(1, series.windowTicks() / TopItems.GROUPS);
        return now / groupTicks;
    }

    /** A janela foi (re)criada: o ranking acompanha (carrega do NBT compatível ou recomeça). */
    private void resetTop(CompoundTag compatible) {
        top = new TopItems();
        if (compatible != null) {
            top.load(compatible.getCompound("top"));
        }
    }

    /** Mesmo formato de antes da {@link MetricSeries} (bucketTicks, buckets, ring, top): saves antigos valem. */
    public CompoundTag save() {
        return series.save(tag -> tag.put("top", top.save()));
    }

    public void load(CompoundTag tag) {
        series.load(tag);
    }
}
