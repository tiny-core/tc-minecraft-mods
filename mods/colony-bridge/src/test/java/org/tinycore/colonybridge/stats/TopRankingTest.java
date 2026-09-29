package org.tinycore.colonybridge.stats;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TopRanking}: o ranking de itens mais entregues (faixa do monitor e aba de estatísticas).
 * As chaves aqui são textos; no jogo são {@code Item}.
 */
class TopRankingTest {

    private static List<String> keys(List<TopRanking.Entry<String>> entries) {
        return entries.stream().map(TopRanking.Entry::key).toList();
    }

    @Test
    void sumsAcrossGroupsAndSortsByCount() {
        TopRanking<String> r = new TopRanking<>(4, 8);
        r.add(0, "iron", 10);
        r.add(1, "iron", 10);
        r.add(1, "bread", 15);
        r.add(2, "log", 5);
        List<TopRanking.Entry<String>> top = r.top(10, 2);
        assertEquals(List.of("iron", "bread", "log"), keys(top));
        assertEquals(20, top.get(0).count());
    }

    @Test
    void limitCutsTheList() {
        TopRanking<String> r = new TopRanking<>(4, 8);
        r.add(0, "a", 3);
        r.add(0, "b", 2);
        r.add(0, "c", 1);
        assertEquals(List.of("a", "b"), keys(r.top(2, 0)));
    }

    @Test
    void fullGroupDropsTheSmallest() {
        TopRanking<String> r = new TopRanking<>(4, 2);
        r.add(0, "big", 100);
        r.add(0, "small", 1);
        r.add(0, "mid", 50); // grupo cheio: "small" sai
        assertEquals(List.of("big", "mid"), keys(r.top(10, 0)));
    }

    @Test
    void groupsOutsideTheWindowAreForgotten() {
        TopRanking<String> r = new TopRanking<>(4, 8);
        r.add(0, "old", 100);
        r.add(3, "recent", 1);
        assertEquals(List.of("old", "recent"), keys(r.top(10, 3)), "grupo 0 ainda está na janela 0..3");
        assertEquals(List.of("recent"), keys(r.top(10, 4)), "no grupo 4 o grupo 0 saiu da janela");
    }

    @Test
    void longPauseClearsEverything() {
        TopRanking<String> r = new TopRanking<>(4, 8);
        r.add(0, "a", 5);
        r.add(1, "b", 5);
        assertTrue(r.top(10, 1000).isEmpty());
    }

    @Test
    void lateAddOutsideTheWindowIsIgnored() {
        TopRanking<String> r = new TopRanking<>(4, 8);
        r.add(10, "now", 1);
        r.add(2, "stale", 99); // grupo 2 já saiu da janela 7..10: não pode cair na vaga de outro grupo
        assertEquals(List.of("now"), keys(r.top(10, 10)));
    }

    @Test
    void resetRestoresClockAndClearsData() {
        TopRanking<String> r = new TopRanking<>(4, 8);
        r.add(5, "a", 1);
        r.reset(-1);
        assertEquals(-1, r.current());
        assertTrue(r.top(10, 5).isEmpty());
    }
}
