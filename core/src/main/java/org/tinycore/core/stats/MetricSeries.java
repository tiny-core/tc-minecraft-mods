package org.tinycore.core.stats;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Contadores numa janela de tempo de jogo (blocos de {@code bucketTicks}, {@code buckets} blocos): a parte
 * comum das estatísticas dos mods TC (ex.: Ponte e Abastecedor do Colony Bridge). Cuida de criar o
 * {@link MetricRing}, somar janelas, montar o gráfico e salvar.
 * <p>
 * O tamanho vem de funções ({@code IntSupplier} ≈ {@code Func<int>} em C#), normalmente a config do mod.
 * Elas só são chamadas quando algo é registrado ou resumido (sempre no servidor), então o block entity pode
 * ser carregado no cliente sem a config. O NBT lido fica pendente até o primeiro uso; se o tamanho mudou,
 * as estatísticas recomeçam do zero.
 */
public final class MetricSeries<E extends Enum<E>> {

    /** Uma hora de jogo em ticks (20 ticks/s × 3600 s). */
    public static final long TICKS_PER_HOUR = 72_000;

    private final Class<E> type;
    private final IntSupplier bucketTicksConfig;
    private final IntSupplier bucketsConfig;
    /** Avisado quando as estruturas são (re)criadas: recebe o NBT compatível, ou null se recomeçou do zero. */
    private final Consumer<@Nullable CompoundTag> onReset;
    private int bucketTicks;
    private @Nullable MetricRing<E> ring;
    private CompoundTag pending = new CompoundTag();
    private boolean dirty;

    /**
     * @param bucketTicks tamanho de cada bloco de tempo, em ticks
     * @param buckets     quantos blocos a janela guarda
     * @param onReset     avisado quando a janela é (re)criada: NBT compatível, ou null se recomeçou do zero
     */
    public MetricSeries(Class<E> type, IntSupplier bucketTicks, IntSupplier buckets,
                        Consumer<@Nullable CompoundTag> onReset) {
        this.type = type;
        this.bucketTicksConfig = bucketTicks;
        this.bucketsConfig = buckets;
        this.onReset = onReset;
    }

    public void add(E metric, long now, long amount) {
        ring().add(metric, now / bucketTicks, amount);
        dirty = true;
    }

    /** Soma da última hora de jogo. */
    public long lastHour(E metric, long now) {
        MetricRing<E> r = ring();
        return r.sumLast(metric, (int) Math.max(1, TICKS_PER_HOUR / bucketTicks), now / bucketTicks);
    }

    /** Soma da janela inteira. */
    public long window(E metric, long now) {
        MetricRing<E> r = ring();
        return r.sumLast(metric, r.size(), now / bucketTicks);
    }

    /** A janela em {@code bars} barras, da mais antiga para a mais recente. */
    public List<Integer> chart(E metric, int bars, long now) {
        return Arrays.stream(ring().grouped(metric, bars, now / bucketTicks)).boxed().toList();
    }

    /** Tamanho da janela em horas (24 com a config padrão). */
    public int windowHours() {
        MetricRing<E> r = ring();
        return (int) Math.max(1, (long) bucketTicks * r.size() / TICKS_PER_HOUR);
    }

    /** Duração da janela inteira em ticks. */
    public long windowTicks() {
        return (long) bucketTicks * ring().size();
    }

    /** true (uma vez) se houve registro desde a última chamada: o block entity precisa ser salvo. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    /** O anel pronto para uso, criado com a config atual (e o NBT pendente, se compatível). */
    private MetricRing<E> ring() {
        int ticks = bucketTicksConfig.getAsInt();
        int buckets = bucketsConfig.getAsInt();
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
    public CompoundTag save(Consumer<CompoundTag> extra) {
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

    public void load(CompoundTag tag) {
        pending = tag.copy();
        ring = null; // recriado no próximo uso, já com a config conferida
    }
}
