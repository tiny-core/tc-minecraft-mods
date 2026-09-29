package org.tinycore.colonybridge.logic.supply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SupplyLineStatus#of}: o texto que o monitor mostra em cada linha do Abastecedor. Um erro aqui
 * aparece no jogo como "Abastecido" com o armazém vazio, ou "Falta na rede" com o item sobrando no ME.
 */
class SupplyLineStatusTest {

    @Test
    void keepLineThatMovedIsRestocking() {
        assertEquals(SupplyLineStatus.RESTOCKING, SupplyLineStatus.of(true, 64, 64, 500, 64, false));
    }

    @Test
    void keepLineAtOrAboveTargetIsStocked() {
        assertEquals(SupplyLineStatus.STOCKED, SupplyLineStatus.of(true, 64, 64, 0, 0, false));
        assertEquals(SupplyLineStatus.STOCKED, SupplyLineStatus.of(true, 200, 64, 0, 0, false));
    }

    @Test
    void keepLineMissingItemsExplainsWhy() {
        assertEquals(SupplyLineStatus.NETWORK_EMPTY, SupplyLineStatus.of(true, 10, 64, 0, 0, false),
                "a rede não tem o item");
        assertEquals(SupplyLineStatus.WAREHOUSE_FULL, SupplyLineStatus.of(true, 10, 64, 500, 0, false),
                "a rede tem, mas nada entrou: racks cheios");
    }

    @Test
    void surplusLineThatMovedIsReturning() {
        assertEquals(SupplyLineStatus.RETURNING, SupplyLineStatus.of(false, 64, 64, 1000, 64, false));
    }

    @Test
    void surplusLineWithinLimitIsOk() {
        assertEquals(SupplyLineStatus.WITHIN_LIMIT, SupplyLineStatus.of(false, 64, 64, 0, 0, false));
        assertEquals(SupplyLineStatus.WITHIN_LIMIT, SupplyLineStatus.of(false, 0, 64, 0, 0, true));
    }

    @Test
    void surplusLineAboveLimitExplainsWhyNothingLeft() {
        assertEquals(SupplyLineStatus.HELD_REQUESTED, SupplyLineStatus.of(false, 200, 64, 0, 0, true),
                "a colônia pediu o item: fica no armazém");
        assertEquals(SupplyLineStatus.NETWORK_FULL, SupplyLineStatus.of(false, 200, 64, 0, 0, false),
                "a rede não aceitou");
    }
}
