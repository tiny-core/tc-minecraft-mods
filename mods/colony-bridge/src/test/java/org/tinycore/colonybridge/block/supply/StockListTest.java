package org.tinycore.colonybridge.block.supply;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StockList}: a migração do formato antigo (9 + 9) para o novo (18 + 18). Um erro aqui aparece no
 * jogo como linha de excedente que virou "manter" depois de atualizar o mod (o bloco passaria a puxar da
 * rede o item que deveria devolver).
 */
class StockListTest {

    @Test
    void oldKeepLinesStayInPlace() {
        assertEquals(0, StockList.migratedIndex(0));
        assertEquals(8, StockList.migratedIndex(8));
    }

    @Test
    void oldSurplusLinesMoveToTheNewSurplusSection() {
        assertEquals(18, StockList.migratedIndex(9));
        assertEquals(26, StockList.migratedIndex(17));
        assertFalse(StockList.isKeep(StockList.migratedIndex(9)), "continua sendo excedente");
    }

    @Test
    void newLayoutHasEighteenLinesPerSection() {
        assertTrue(StockList.isKeep(17));
        assertFalse(StockList.isKeep(18));
        assertEquals(36, StockList.SIZE);
    }
}
