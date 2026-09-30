package org.tinycore.colonybridge.logic.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TabletCharge}: erros aqui aparecem no jogo como bateria passando da capacidade, carga "negativa"
 * (tirando energia do tablet) ou barra do ícone errada.
 */
class TabletChargeTest {

    @Test
    void chargesRateTimesTicks() {
        assertEquals(10_000, TabletCharge.step(0, 100_000, 1_000, 10));
    }

    @Test
    void neverPassesCapacity() {
        assertEquals(500, TabletCharge.step(99_500, 100_000, 1_000, 10));
        assertEquals(0, TabletCharge.step(100_000, 100_000, 1_000, 10));
        assertEquals(0, TabletCharge.step(120_000, 100_000, 1_000, 10), "acima da capacidade não carrega");
    }

    @Test
    void hugeRateDoesNotOverflow() {
        assertEquals(100_000, TabletCharge.step(0, 100_000, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void negativeInputsChargeNothing() {
        assertEquals(0, TabletCharge.step(0, 100_000, -5, 10));
        assertEquals(0, TabletCharge.step(0, 100_000, 5, -10));
    }

    @Test
    void barWidthFollowsFraction() {
        assertEquals(0, TabletCharge.barWidth(0, 100));
        assertEquals(13, TabletCharge.barWidth(100, 100));
        assertEquals(7, TabletCharge.barWidth(50, 100));
        assertEquals(13, TabletCharge.barWidth(500, 100));
        assertEquals(0, TabletCharge.barWidth(50, 0));
    }
}
