package org.tinycore.colonybridge.logic.encoder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeRankingTest {

    /** missing, inputs, produced. */
    private record R(int missing, int inputs, int produced) {}

    private static int best(List<R> list) {
        return RecipeRanking.best(list, R::missing, R::inputs, R::produced);
    }

    @Test
    void emptyListHasNoBest() {
        assertEquals(-1, best(List.of()));
    }

    @Test
    void fewerMissingIngredientsWinsOverCheaper() {
        assertEquals(1, best(List.of(new R(1, 1, 4), new R(0, 8, 1))));
    }

    @Test
    void sameMissingPrefersFewerInputsPerItem() {
        assertEquals(1, best(List.of(new R(0, 4, 1), new R(0, 4, 2))));
    }

    @Test
    void tieKeepsTheFirst() {
        assertEquals(0, best(List.of(new R(0, 3, 1), new R(0, 3, 1))));
    }

    @Test
    void zeroProducedCountsAsOne() {
        assertEquals(1, best(List.of(new R(0, 3, 0), new R(0, 2, 0))));
    }
}
