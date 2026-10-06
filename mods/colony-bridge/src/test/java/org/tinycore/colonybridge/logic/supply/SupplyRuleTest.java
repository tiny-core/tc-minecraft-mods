package org.tinycore.colonybridge.logic.supply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SupplyRule}: quanto o Abastecedor move por linha. Os erros aqui aparecem no jogo como item
 * sumindo do armazém, vaivém com a ponte ou ciclo movendo demais de uma vez.
 */
class SupplyRuleTest {

    @Test
    void restockFillsOnlyWhatIsMissing() {
        assertEquals(36, SupplyRule.restock(28, 64, 1000));
    }

    @Test
    void restockRespectsThePerCycleCap() {
        assertEquals(100, SupplyRule.restock(0, 5000, 100));
    }

    @Test
    void restockDoesNothingAtOrAboveTarget() {
        assertEquals(0, SupplyRule.restock(64, 64, 1000));
        assertEquals(0, SupplyRule.restock(200, 64, 1000), "acima da meta não tira nada (nem negativo)");
    }

    @Test
    void surplusReturnsOnlyWhatExceedsTarget() {
        assertEquals(36, SupplyRule.surplus(100, 64, false, 1000));
        assertEquals(100, SupplyRule.surplus(5000, 64, false, 100), "limitado pelo teto do ciclo");
    }

    @Test
    void surplusNeverTouchesRequestedItems() {
        // A colônia está pedindo o item: devolver criaria vaivém com a ponte.
        assertEquals(0, SupplyRule.surplus(5000, 64, true, 1000));
    }

    @Test
    void surplusDoesNothingAtOrBelowTarget() {
        assertEquals(0, SupplyRule.surplus(64, 64, false, 1000));
        assertEquals(0, SupplyRule.surplus(10, 64, false, 1000));
    }

    @Test
    void allocateTakesFromFirstSourceThenNext() {
        assertArrayEquals(new long[]{30, 10, 0}, SupplyRule.allocate(40, new long[]{30, 20, 5}));
    }

    @Test
    void allocateNeverTakesMoreThanAvailable() {
        assertArrayEquals(new long[]{3, 2}, SupplyRule.allocate(100, new long[]{3, 2}));
    }

    @Test
    void allocateIgnoresNegativeAndZeroWanted() {
        assertArrayEquals(new long[]{0, 4}, SupplyRule.allocate(4, new long[]{-7, 9}));
        assertArrayEquals(new long[]{0, 0}, SupplyRule.allocate(0, new long[]{5, 5}));
        assertArrayEquals(new long[]{0}, SupplyRule.allocate(-3, new long[]{5}));
    }

    @Test
    void craftAsksForTheWholeShortfallWhenTheNetworkIsEmpty() {
        assertEquals(54, SupplyRule.craft(true, 10, 64, 0, false));
    }

    @Test
    void craftNeverRunsWhileTheNetworkStillHasTheItem() {
        assertEquals(0, SupplyRule.craft(true, 10, 64, 5, false));
    }

    @Test
    void craftDoesNotStackJobsOrRunWhenStockedOrDisabled() {
        assertEquals(0, SupplyRule.craft(true, 10, 64, 0, true), "já há craft rodando");
        assertEquals(0, SupplyRule.craft(true, 64, 64, 0, false), "meta atingida");
        assertEquals(0, SupplyRule.craft(false, 10, 64, 0, false), "desligado no bloco ou na config");
    }
}
