package org.tinycore.core.stats;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MetricSeries}: estatísticas salvas no NBT dos blocos. O que importa aqui é não perder dados de
 * mundos existentes e recomeçar do zero (em vez de mostrar lixo) quando a config da janela muda.
 */
class MetricSeriesTest {

    private enum TestMetric { A, B }

    /** 1 hora = 72.000 ticks; blocos de 6.000 ticks (5 min), 288 blocos = 24 h, como a config padrão. */
    private static MetricSeries<TestMetric> series(int bucketTicks, int buckets, List<CompoundTag> resets) {
        return new MetricSeries<>(TestMetric.class, () -> bucketTicks, () -> buckets, resets::add);
    }

    private static MetricSeries<TestMetric> series(int bucketTicks, int buckets) {
        return series(bucketTicks, buckets, new ArrayList<>());
    }

    @Test
    void lastHourAndWindowSums() {
        MetricSeries<TestMetric> s = series(6000, 288);
        long now = 100 * MetricSeries.TICKS_PER_HOUR;
        s.add(TestMetric.A, now - 2 * MetricSeries.TICKS_PER_HOUR, 7); // 2 h atrás: só na janela
        s.add(TestMetric.A, now, 3);
        assertEquals(3, s.lastHour(TestMetric.A, now));
        assertEquals(10, s.window(TestMetric.A, now));
        assertEquals(0, s.window(TestMetric.B, now));
        assertEquals(24, s.windowHours());
    }

    @Test
    void nbtRoundTripKeepsTheNumbers() {
        MetricSeries<TestMetric> s = series(6000, 288);
        s.add(TestMetric.A, 50_000, 42);
        MetricSeries<TestMetric> loaded = series(6000, 288);
        loaded.load(s.save(tag -> {}));
        assertEquals(42, loaded.window(TestMetric.A, 50_000));
    }

    @Test
    void savingBeforeFirstUseKeepsTheLoadedData() {
        // Bloco carregado e salvo de novo sem registrar nada (ex.: chunk entrou e saiu): não pode zerar.
        MetricSeries<TestMetric> original = series(6000, 288);
        original.add(TestMetric.A, 10, 5);
        CompoundTag saved = original.save(tag -> {});

        MetricSeries<TestMetric> untouched = series(6000, 288);
        untouched.load(saved);
        MetricSeries<TestMetric> again = series(6000, 288);
        again.load(untouched.save(tag -> {}));
        assertEquals(5, again.window(TestMetric.A, 10));
    }

    @Test
    void configChangeStartsFromZero() {
        MetricSeries<TestMetric> s = series(6000, 288);
        s.add(TestMetric.A, 10, 5);
        List<CompoundTag> resets = new ArrayList<>();
        MetricSeries<TestMetric> changed = series(1200, 288, resets);
        changed.load(s.save(tag -> {}));
        assertEquals(0, changed.window(TestMetric.A, 10));
        assertEquals(1, resets.size());
        assertNull(resets.get(0), "dono avisado com null: recomeçou do zero");
    }

    @Test
    void ownerExtraDataTravelsWithTheSeries() {
        // A Ponte grava o ranking ("top") na mesma tag e o recebe de volta no onReset.
        MetricSeries<TestMetric> s = series(6000, 288);
        s.add(TestMetric.A, 10, 1);
        CompoundTag saved = s.save(tag -> tag.putString("top", "dados do dono"));

        List<CompoundTag> resets = new ArrayList<>();
        MetricSeries<TestMetric> loaded = series(6000, 288, resets);
        loaded.load(saved);
        loaded.window(TestMetric.A, 10); // primeiro uso cria o anel
        assertNotNull(resets.get(0));
        assertEquals("dados do dono", resets.get(0).getString("top"));
    }

    @Test
    void dirtyFlagIsConsumedOnce() {
        MetricSeries<TestMetric> s = series(6000, 288);
        s.add(TestMetric.A, 10, 1);
        assertTrue(s.consumeDirty());
        assertEquals(false, s.consumeDirty());
    }
}
