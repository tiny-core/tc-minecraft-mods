package org.tinycore.colonybridge.logic.terminal;

import org.junit.jupiter.api.Test;
import org.tinycore.colonybridge.logic.BridgeStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TerminalLink}: as restrições de rede. Um erro aqui aparece no jogo como terminal funcionando sem
 * Ponte (fica "OP" de novo) ou duas Pontes disputando a mesma rede.
 */
class TerminalLinkTest {

    @Test
    void onlyOneBridgePerNetwork() {
        assertTrue(TerminalLink.bridgeAllowed(1));
        assertFalse(TerminalLink.bridgeAllowed(2));
    }

    @Test
    void terminalNeedsAnActiveBridgeOfTheSameColony() {
        assertEquals(BridgeStatus.IDLE, TerminalLink.terminalStatus(1, true, true));
        assertEquals(BridgeStatus.NO_BRIDGE, TerminalLink.terminalStatus(0, false, false), "sem Ponte na rede");
        assertEquals(BridgeStatus.NO_BRIDGE, TerminalLink.terminalStatus(1, false, true), "Ponte offline");
        assertEquals(BridgeStatus.NO_BRIDGE, TerminalLink.terminalStatus(1, true, false), "Ponte de outra colônia");
    }

    @Test
    void twoBridgesBlockTheTerminalToo() {
        assertEquals(BridgeStatus.DUPLICATE_BRIDGE, TerminalLink.terminalStatus(2, true, true));
    }

    @Test
    void activeMeansIdleOrWorking() {
        assertTrue(TerminalLink.isActive(BridgeStatus.IDLE));
        assertTrue(TerminalLink.isActive(BridgeStatus.WORKING));
        assertFalse(TerminalLink.isActive(BridgeStatus.OFFLINE));
        assertFalse(TerminalLink.isActive(BridgeStatus.DUPLICATE_BRIDGE));
    }
}
