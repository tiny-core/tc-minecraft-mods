package org.tinycore.colonybridge.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link MonitorRequestList}: quantos pedidos cabem numa página do monitor (medidas em pixels de tela). */
class MonitorRequestListTest {

    @Test
    void nothingFitsInATinyArea() {
        assertEquals(0, MonitorRequestList.perPage(64, 20), "menor que uma linha + rodapé");
    }

    @Test
    void oneColumnBelowFourBlocksWide() {
        assertEquals(5, MonitorRequestList.perPage(64, 110));
    }

    @Test
    void twoColumnsFromFourBlocksWide() {
        assertEquals(10, MonitorRequestList.perPage(256, 110));
    }
}
