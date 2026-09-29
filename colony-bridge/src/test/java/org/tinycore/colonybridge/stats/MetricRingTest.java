package org.tinycore.colonybridge.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MetricRing}: o anel precisa esquecer o que saiu da janela, somar certo e sobreviver ao NBT —
 * é o que alimenta os números dos monitores.
 */
class MetricRingTest {

    @Test
    void sumsOnlyTheRequestedBuckets() {
        MetricRing<SupplyMetric> ring = new MetricRing<>(SupplyMetric.class, 10);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 5, 3);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 6, 4);
        ring.add(SupplyMetric.ITEMS_RETURNED, 6, 100); // outra métrica não entra na soma
        assertEquals(4, ring.sumLast(SupplyMetric.ITEMS_RESTOCKED, 1, 6));
        assertEquals(7, ring.sumLast(SupplyMetric.ITEMS_RESTOCKED, 10, 6));
    }

    @Test
    void oldBucketsAreForgottenWhenTimeWrapsAround() {
        MetricRing<SupplyMetric> ring = new MetricRing<>(SupplyMetric.class, 4);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 1, 50);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 5, 7); // mesma posição do bloco 1, uma volta depois
        assertEquals(7, ring.sumLast(SupplyMetric.ITEMS_RESTOCKED, 4, 5));
        assertEquals(0, ring.sumLast(SupplyMetric.ITEMS_RESTOCKED, 4, 20), "janela inteira já passou");
    }

    @Test
    void countersSaturateInsteadOfOverflowing() {
        MetricRing<SupplyMetric> ring = new MetricRing<>(SupplyMetric.class, 2);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 0, Integer.MAX_VALUE);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 0, 10);
        assertEquals(Integer.MAX_VALUE, ring.sumLast(SupplyMetric.ITEMS_RESTOCKED, 1, 0));
    }

    @Test
    void groupedSplitsTheWindowOldestFirst() {
        MetricRing<SupplyMetric> ring = new MetricRing<>(SupplyMetric.class, 4);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 0, 1);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 1, 2);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 2, 3);
        ring.add(SupplyMetric.ITEMS_RESTOCKED, 3, 4);
        assertArrayEquals(new int[]{3, 7}, ring.grouped(SupplyMetric.ITEMS_RESTOCKED, 2, 3));
    }

    @Test
    void nbtRoundTripAndSizeChangeIsIgnored() {
        MetricRing<StatMetric> ring = new MetricRing<>(StatMetric.class, 6);
        ring.add(StatMetric.CRAFTS_DONE, 3, 5);
        MetricRing<StatMetric> same = new MetricRing<>(StatMetric.class, 6);
        same.load(ring.save());
        assertEquals(5, same.sumLast(StatMetric.CRAFTS_DONE, 6, 3));

        MetricRing<StatMetric> resized = new MetricRing<>(StatMetric.class, 8); // config mudou
        resized.load(ring.save());
        assertEquals(0, resized.sumLast(StatMetric.CRAFTS_DONE, 8, 3));
    }
}
