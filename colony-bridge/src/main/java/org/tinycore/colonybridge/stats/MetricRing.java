package org.tinycore.colonybridge.stats;

import net.minecraft.nbt.CompoundTag;

/**
 * Buffer circular (ring buffer) de contadores: um array de tamanho fixo por métrica, onde cada posição
 * é um bloco de tempo. Quando o tempo avança, as posições mais antigas são zeradas e reaproveitadas,
 * então a memória nunca cresce e somar a janela custa sempre o mesmo.
 * <p>
 * Os blocos são identificados pelo número absoluto {@code gameTime / bucketTicks}; a posição no array
 * é esse número módulo o tamanho.
 * <p>
 * {@code <E extends Enum<E>>}: as métricas são um enum qualquer (as da Ponte, as do Abastecedor...), como
 * um generic com {@code where E : Enum} em C#. Cada valor do enum vira uma linha de contadores.
 */
final class MetricRing<E extends Enum<E>> {

    private final E[] metrics;
    private final int size;
    private final int[][] values;
    /** Último bloco absoluto já "aberto" (tudo depois dele ainda não existe). */
    private long current = -1;

    MetricRing(Class<E> type, int size) {
        this.metrics = type.getEnumConstants();
        this.size = size;
        this.values = new int[metrics.length][size];
    }

    int size() {
        return size;
    }

    void add(E metric, long bucket, long amount) {
        advance(bucket);
        int[] row = values[metric.ordinal()];
        int index = index(bucket);
        row[index] = (int) Math.min(Integer.MAX_VALUE, (long) row[index] + amount); // satura em vez de estourar
    }

    /** Soma dos últimos {@code buckets} blocos, terminando em {@code nowBucket}. */
    long sumLast(E metric, int buckets, long nowBucket) {
        advance(nowBucket);
        int[] row = values[metric.ordinal()];
        long sum = 0;
        for (int i = 0; i < Math.min(buckets, size); i++) {
            sum += row[index(nowBucket - i)];
        }
        return sum;
    }

    /**
     * A janela inteira agrupada em {@code groups} partes (ex.: 288 blocos de 5 min → 24 horas),
     * da mais antiga para a mais recente. Usado no gráfico.
     */
    int[] grouped(E metric, int groups, long nowBucket) {
        advance(nowBucket);
        int[] row = values[metric.ordinal()];
        int perGroup = Math.max(1, size / groups);
        int[] result = new int[groups];
        for (int g = 0; g < groups; g++) {
            long groupEnd = nowBucket - (long) (groups - 1 - g) * perGroup;
            long sum = 0;
            for (int i = 0; i < perGroup; i++) {
                sum += row[index(groupEnd - i)];
            }
            result[g] = (int) Math.min(Integer.MAX_VALUE, sum);
        }
        return result;
    }

    /** Abre os blocos entre o atual e {@code bucket}, zerando o que sobrou de voltas anteriores do anel. */
    private void advance(long bucket) {
        if (bucket <= current) {
            return;
        }
        long steps = current < 0 ? size : Math.min(bucket - current, size);
        for (long i = 0; i < steps; i++) {
            int index = index(bucket - i);
            for (int[] row : values) {
                row[index] = 0;
            }
        }
        current = bucket;
    }

    private int index(long bucket) {
        return (int) Math.floorMod(bucket, (long) size);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("current", current);
        for (E metric : metrics) {
            tag.putIntArray(metric.name().toLowerCase(), values[metric.ordinal()]);
        }
        return tag;
    }

    /** Lê do NBT; arrays de tamanho diferente (config mudou) são ignorados e ficam zerados. */
    void load(CompoundTag tag) {
        current = tag.contains("current") ? tag.getLong("current") : -1;
        for (E metric : metrics) {
            int[] saved = tag.getIntArray(metric.name().toLowerCase());
            if (saved.length == size) {
                System.arraycopy(saved, 0, values[metric.ordinal()], 0, size);
            }
        }
    }
}
