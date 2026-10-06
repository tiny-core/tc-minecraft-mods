package org.tinycore.core.grid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GridHeightTest {

    @Test
    void smallUsesFiveRowsWhenTheyFit() {
        assertEquals(5, GridHeight.SMALL.rows(2, 12, 18 * 10, 18));
    }

    @Test
    void mediumUsesEightRowsWhenTheyFit() {
        assertEquals(8, GridHeight.MEDIUM.rows(2, 12, 18 * 10, 18));
    }

    @Test
    void tallFillsTheWindowUpToTheCap() {
        assertEquals(7, GridHeight.TALL.rows(2, 12, 18 * 5 + 10, 18));
        assertEquals(12, GridHeight.TALL.rows(2, 12, 18 * 40, 18));
    }

    @Test
    void smallWindowShrinksTheGridButNeverBelowTheMinimum() {
        assertEquals(3, GridHeight.SMALL.rows(2, 12, 18, 18));
        assertEquals(2, GridHeight.MEDIUM.rows(2, 12, -50, 18));
    }

    @Test
    void cycleWrapsAround() {
        assertEquals(GridHeight.MEDIUM, GridHeight.SMALL.next());
        assertEquals(GridHeight.TALL, GridHeight.MEDIUM.next());
        assertEquals(GridHeight.SMALL, GridHeight.TALL.next());
    }
}
