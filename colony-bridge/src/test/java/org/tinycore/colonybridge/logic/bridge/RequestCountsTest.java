package org.tinycore.colonybridge.logic.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link RequestCounts}: o cartão "Pendentes" da aba Geral nunca pode ficar negativo. */
class RequestCountsTest {

    @Test
    void pendingIsWhatIsLeft() {
        assertEquals(3, new RequestCounts(10, 5, 2).pending());
    }

    @Test
    void pendingNeverNegative() {
        assertEquals(0, new RequestCounts(1, 5, 2).pending());
    }
}
