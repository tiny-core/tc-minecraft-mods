package org.tinycore.colonybridge.stats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import org.tinycore.colonybridge.Config;

import java.util.Arrays;
import java.util.List;

/**
 * Estatísticas de uma ponte: registra eventos (entregas, crafts) de forma incremental e monta o
 * {@link StatsSummary} para a tela e os monitores.
 * <p>
 * Memória constante: contadores em {@link MetricRing} (blocos de {@code statsBucketTicks}) e ranking
 * em {@link TopItems}. O tempo é o do jogo ({@code gameTime}): com o servidor parado, nada anda.
 * <p>
 * A config só é lida quando a ponte registra ou resume algo, o que só acontece no servidor. Assim o
 * block entity pode ser criado e carregado no cliente sem depender da config do servidor.
 * Se {@code statsBucketTicks} ou {@code statsBuckets} mudarem na config, as estatísticas são zeradas.
 */
public final class BridgeStats {

    /** Uma hora de jogo em ticks (20 ticks/s × 3600 s). */
    private static final long TICKS_PER_HOUR = 72_000;
    private static final int CHART_BARS = 24;

    private int bucketTicks;
    private MetricRing ring;
    private TopItems top = new TopItems();
    /** NBT lido antes da config estar disponível; aplicado no primeiro uso. */
    private CompoundTag pending = new CompoundTag();
    /** Algo foi registrado desde a última consulta: o block entity precisa ser salvo. */
    private boolean dirty;

    public void recordDelivery(long now, Item item, long amount) {
        ensureReady();
        long bucket = now / bucketTicks;
        ring.add(StatMetric.REQUESTS_DELIVERED, bucket, 1);
        ring.add(StatMetric.ITEMS_DELIVERED, bucket, amount);
        top.add(topGroup(now), item, amount);
        dirty = true;
    }

    public void recordCraftStarted(long now) {
        ensureReady();
        ring.add(StatMetric.CRAFTS_STARTED, now / bucketTicks, 1);
        dirty = true;
    }

    public void recordCraftFailed(long now) {
        ensureReady();
        ring.add(StatMetric.CRAFTS_FAILED, now / bucketTicks, 1);
        dirty = true;
    }

    /** true (uma vez) se houve registro desde a última chamada. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public StatsSummary summary(long now) {
        ensureReady();
        long bucket = now / bucketTicks;
        int hourBuckets = (int) Math.max(1, TICKS_PER_HOUR / bucketTicks);
        int all = ring.size();
        StatsSummary.Totals totals = new StatsSummary.Totals(
                ring.sumLast(StatMetric.ITEMS_DELIVERED, hourBuckets, bucket),
                ring.sumLast(StatMetric.REQUESTS_DELIVERED, hourBuckets, bucket),
                ring.sumLast(StatMetric.ITEMS_DELIVERED, all, bucket),
                ring.sumLast(StatMetric.REQUESTS_DELIVERED, all, bucket),
                ring.sumLast(StatMetric.CRAFTS_STARTED, all, bucket),
                ring.sumLast(StatMetric.CRAFTS_FAILED, all, bucket));
        List<Integer> chart = Arrays.stream(ring.grouped(StatMetric.ITEMS_DELIVERED, CHART_BARS, bucket)).boxed().toList();
        List<StatsSummary.Top> ranking = top.top(StatsSummary.MAX_TOP, topGroup(now)).stream()
                .map(e -> new StatsSummary.Top(e.item(), e.count()))
                .toList();
        int hours = (int) Math.max(1, (long) bucketTicks * all / TICKS_PER_HOUR);
        return new StatsSummary(hours, totals, chart, ranking);
    }

    /** Cada grupo do ranking cobre 1/{@link TopItems#GROUPS} da janela (1 hora com a config padrão). */
    private long topGroup(long now) {
        long groupTicks = Math.max(1, (long) bucketTicks * ring.size() / TopItems.GROUPS);
        return now / groupTicks;
    }

    /** Cria as estruturas com a config atual e aplica o NBT pendente, se ele for compatível. */
    private void ensureReady() {
        int ticks = Config.STATS_BUCKET_TICKS.get();
        int buckets = Config.STATS_BUCKETS.get();
        if (ring != null && bucketTicks == ticks && ring.size() == buckets) {
            return;
        }
        boolean compatible = pending.getInt("bucketTicks") == ticks && pending.getInt("buckets") == buckets;
        bucketTicks = ticks;
        ring = new MetricRing(buckets);
        top = new TopItems();
        if (compatible) {
            ring.load(pending.getCompound("ring"));
            top.load(pending.getCompound("top"));
        }
        pending = new CompoundTag();
    }

    /** Salva sem precisar da config: se ainda não foi usada, regrava o NBT que foi lido. */
    public CompoundTag save() {
        if (ring == null) {
            return pending.copy();
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("bucketTicks", bucketTicks);
        tag.putInt("buckets", ring.size());
        tag.put("ring", ring.save());
        tag.put("top", top.save());
        return tag;
    }

    public void load(CompoundTag tag) {
        pending = tag.copy();
        ring = null; // reaplicado no próximo uso, já com a config conferida
    }
}
