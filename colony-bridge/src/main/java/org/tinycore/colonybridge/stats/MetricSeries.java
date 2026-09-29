package org.tinycore.colonybridge.stats;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Contadores numa janela de tempo de jogo, com o tamanho vindo da config ({@code statsBucketTicks} ×
 * {@code statsBuckets}): a parte comum das estatísticas da Ponte ({@link BridgeStats}) e do Abastecedor
 * ({@link SupplyStats}). Cuida de criar o {@link MetricRing}, somar janelas, montar o gráfico e salvar.
 * <p>
 * A config só é lida quando algo é registrado ou resumido (sempre no servidor), então o block entity pode
 * ser carregado no cliente sem ela. O NBT lido fica pendente até o primeiro uso; se a config mudou de
 * tamanho, as estatísticas recomeçam do zero.
 */
final class MetricSeries<E extends Enum<E>> {

    /** Uma hora de jogo em ticks (20 ticks/s × 3600 s). */
    static final long TICKS_PER_HOUR = 72_000;

    private final Class<E> type;
    /** Avisado quando as estruturas são (re)criadas: recebe o NBT compatível, ou null se recomeçou do zero. */
    private final Consumer<@Nullable CompoundTag> onReset;
    private int bucketTicks;
    private @Nullable MetricRing<E> ring;
    private CompoundTag pending = new CompoundTag();
    private boolean dirty;

    MetricSeries(Class<E> type, Consumer<@Nullable CompoundTag> onReset) {
        this.type = type;
        this.onReset = onReset;
    }

    void add(E metric, long now, long amount) {
        ring().add(metric, now / bucketTicks, amount);
        dirty = true;
    }

    /** Soma da última hora de jogo. */
    long lastHour(E metric, long now) {
        MetricRing<E> r = ring();
        return r.sumLast(metric, (int) Math.max(1, TICKS_PER_HOUR / bucketTicks), now / bucketTicks);
    }

    /** Soma da janela inteira. */
    long window(E metric, long now) {
        MetricRing<E> r = ring();
        return r.sumLast(metric, r.size(), now / bucketTicks);
    }

    /** A janela em {@code bars} barras, da mais antiga para a mais recente. */
    List<Integer> chart(E metric, int bars, long now) {
        return Arrays.stream(ring().grouped(metric, bars, now / bucketTicks)).boxed().toList();
    }

    /** Tamanho da janela em horas (24 com a config padrão). */
    int windowHours() {
        MetricRing<E> r = ring();
        return (int) Math.max(1, (long) bucketTicks * r.size() / TICKS_PER_HOUR);
    }

    /** Duração da janela inteira em ticks. */
    long windowTicks() {
        return (long) bucketTicks * ring().size();
    }

    /** true (uma vez) se houve registro desde a última chamada: o block entity precisa ser salvo. */
    boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    /** O anel pronto para uso, criado com a config atual (e o NBT pendente, se compatível). */
    private MetricRing<E> ring() {
        int ticks = Config.STATS_BUCKET_TICKS.get();
        int buckets = Config.STATS_BUCKETS.get();
        if (ring != null && bucketTicks == ticks && ring.size() == buckets) {
            return ring;
        }
        boolean compatible = pending.getInt("bucketTicks") == ticks && pending.getInt("buckets") == buckets;
        bucketTicks = ticks;
        ring = new MetricRing<>(type, buckets);
        if (compatible) {
            ring.load(pending.getCompound("ring"));
        }
        onReset.accept(compatible ? pending : null);
        pending = new CompoundTag();
        return ring;
    }

    /**
     * Salva sem precisar da config: se ainda não foi usada, regrava o NBT que foi lido.
     *
     * @param extra dados do dono gravados na mesma tag (ex.: ranking de itens); não chamado se nada foi usado
     */
    CompoundTag save(Consumer<CompoundTag> extra) {
        if (ring == null) {
            return pending.copy();
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("bucketTicks", bucketTicks);
        tag.putInt("buckets", ring.size());
        tag.put("ring", ring.save());
        extra.accept(tag);
        return tag;
    }

    void load(CompoundTag tag) {
        pending = tag.copy();
        ring = null; // recriado no próximo uso, já com a config conferida
    }
}
