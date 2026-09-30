package org.tinycore.colonybridge.logic.colony;

import org.junit.jupiter.api.Test;
import org.tinycore.colonybridge.logic.colony.ColonySlotRule.HolderState;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ColonySlotRule}: quando um bloco pode ficar com a vaga "um por colônia". Protege contra os dois
 * erros possíveis: dois blocos ativos na mesma colônia, e vaga presa num bloco que já não existe.
 */
class ColonySlotRuleTest {

    private static final long MINE = 100L;
    private static final long OTHER = 200L;

    @Test
    void freeSlotCanBeClaimed() {
        assertTrue(ColonySlotRule.canClaim(null, MINE, HolderState.VALID));
    }

    @Test
    void ownSlotStaysMine() {
        assertTrue(ColonySlotRule.canClaim(MINE, MINE, HolderState.VALID));
    }

    @Test
    void validOtherBlockKeepsSlot() {
        assertFalse(ColonySlotRule.canClaim(OTHER, MINE, HolderState.VALID));
    }

    @Test
    void unloadedOtherBlockKeepsSlot() {
        assertFalse(ColonySlotRule.canClaim(OTHER, MINE, HolderState.UNLOADED));
    }

    @Test
    void staleRegistrationIsReplaced() {
        assertTrue(ColonySlotRule.canClaim(OTHER, MINE, HolderState.GONE));
    }
}
