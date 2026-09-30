package org.tinycore.colonybridge.logic.tablet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TabletTabs}: qual aba o tablet abre. Um erro aqui aparece no jogo como tela aberta de um bloco que
 * não existe (pacote hostil pedindo aba indisponível) ou tablet abrindo na aba errada.
 */
class TabletTabsTest {

    private static int mask(TabletTab... tabs) {
        int mask = 0;
        for (TabletTab tab : tabs) {
            mask = TabletTabs.with(mask, tab, true);
        }
        return mask;
    }

    @Test
    void terminalIsTheMainTab() {
        assertEquals(TabletTab.TERMINAL, TabletTabs.choose(mask(TabletTab.SUPPLY, TabletTab.TERMINAL), null));
    }

    @Test
    void withoutTerminalOpensTheNextAvailable() {
        assertEquals(TabletTab.BRIDGE, TabletTabs.choose(mask(TabletTab.BRIDGE, TabletTab.SUPPLY), null));
        assertEquals(TabletTab.SUPPLY, TabletTabs.choose(mask(TabletTab.SUPPLY), null));
    }

    @Test
    void nothingAvailableOpensNothing() {
        assertNull(TabletTabs.choose(0, null));
    }

    @Test
    void wantedTabMustBeAvailable() {
        int mask = mask(TabletTab.TERMINAL);
        assertEquals(TabletTab.TERMINAL, TabletTabs.choose(mask, TabletTab.TERMINAL));
        assertNull(TabletTabs.choose(mask, TabletTab.SUPPLY), "aba pedida indisponível não abre outra no lugar");
    }

    @Test
    void tabOutsideTheMaskIsUnavailable() {
        assertFalse(TabletTabs.isAvailable(mask(TabletTab.TERMINAL), TabletTab.CHUNK_LOADER));
        assertTrue(TabletTabs.isAvailable(mask(TabletTab.CHUNK_LOADER), TabletTab.CHUNK_LOADER));
    }

    @Test
    void withTurnsBitsOnAndOff() {
        int mask = mask(TabletTab.TERMINAL, TabletTab.BRIDGE);
        assertTrue(TabletTabs.isAvailable(mask, TabletTab.BRIDGE));
        mask = TabletTabs.with(mask, TabletTab.BRIDGE, false);
        assertFalse(TabletTabs.isAvailable(mask, TabletTab.BRIDGE));
        assertTrue(TabletTabs.isAvailable(mask, TabletTab.TERMINAL));
    }

    @Test
    void drainStepIsSafe() {
        assertEquals(100, TabletTabs.drainStep(5, 20));
        assertEquals(0, TabletTabs.drainStep(-5, 20));
        assertEquals(Integer.MAX_VALUE, TabletTabs.drainStep(Integer.MAX_VALUE, 20));
    }
}
