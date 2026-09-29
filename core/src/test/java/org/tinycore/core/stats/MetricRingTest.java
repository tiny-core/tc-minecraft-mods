package org.tinycore.core.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MetricRing}: o anel precisa esquecer o que saiu da janela, somar certo e sobreviver ao NBT —
 * é o que alimenta os números dos monitores dos mods TC.
 */
class MetricRingTest {

    /** Métricas só do teste: o anel aceita qualquer enum. */
    private enum TestMetric { A, B }

    @Test
    void sumsOnlyTheRequestedBuckets() {
        MetricRing<TestMetric> ring = new MetricRing<>(TestMetric.class, 10);
        ring.add(TestMetric.A, 5, 3);
        ring.add(TestMetric.A, 6, 4);
        ring.add(TestMetric.B, 6, 100); // outra métrica não entra na soma
        assertEquals(4, ring.sumLast(TestMetric.A, 1, 6));
        assertEquals(7, ring.sumLast(TestMetric.A, 10, 6));
    }

    @Test
    void oldBucketsAreForgottenWhenTimeWrapsAround() {
        MetricRing<TestMetric> ring = new MetricRing<>(TestMetric.class, 4);
        ring.add(TestMetric.A, 1, 50);
        ring.add(TestMetric.A, 5, 7); // mesma posição do bloco 1, uma volta depois
        assertEquals(7, ring.sumLast(TestMetric.A, 4, 5));
        assertEquals(0, ring.sumLast(TestMetric.A, 4, 20), "janela inteira já passou");
    }

    @Test
    void countersSaturateInsteadOfOverflowing() {
        MetricRing<TestMetric> ring = new MetricRing<>(TestMetric.class, 2);
        ring.add(TestMetric.A, 0, Integer.MAX_VALUE);
        ring.add(TestMetric.A, 0, 10);
        assertEquals(Integer.MAX_VALUE, ring.sumLast(TestMetric.A, 1, 0));
    }

    @Test
    void groupedSplitsTheWindowOldestFirst() {
        MetricRing<TestMetric> ring = new MetricRing<>(TestMetric.class, 4);
        ring.add(TestMetric.A, 0, 1);
        ring.add(TestMetric.A, 1, 2);
        ring.add(TestMetric.A, 2, 3);
        ring.add(TestMetric.A, 3, 4);
        assertArrayEquals(new int[]{3, 7}, ring.grouped(TestMetric.A, 2, 3));
    }

    @Test
    void nbtRoundTripAndSizeChangeIsIgnored() {
        MetricRing<TestMetric> ring = new MetricRing<>(TestMetric.class, 6);
        ring.add(TestMetric.B, 3, 5);
        MetricRing<TestMetric> same = new MetricRing<>(TestMetric.class, 6);
        same.load(ring.save());
        assertEquals(5, same.sumLast(TestMetric.B, 6, 3));

        MetricRing<TestMetric> resized = new MetricRing<>(TestMetric.class, 8); // config mudou
        resized.load(ring.save());
        assertEquals(0, resized.sumLast(TestMetric.B, 8, 3));
    }
}
