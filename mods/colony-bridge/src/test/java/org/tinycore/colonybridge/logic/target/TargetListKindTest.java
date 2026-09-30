package org.tinycore.colonybridge.logic.target;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TargetListKind}: o que cada lista aceita. Um erro aqui aparece no jogo como "manter tudo" (a rede ME
 * inteira despejada no armazém) ou {@code @mod} em "manter" (sem saber o que trazer).
 */
class TargetListKindTest {

    @Test
    void keepAcceptsItemAndTagButNotMod() {
        assertTrue(TargetListKind.KEEP.allows(TargetKind.ITEM));
        assertTrue(TargetListKind.KEEP.allows(TargetKind.TAG));
        assertFalse(TargetListKind.KEEP.allows(TargetKind.MOD));
    }

    @Test
    void surplusAndFilterAcceptEverything() {
        for (TargetKind kind : TargetKind.values()) {
            assertTrue(TargetListKind.SURPLUS.allows(kind));
            assertTrue(TargetListKind.FILTER.allows(kind));
        }
    }

    @Test
    void allOnlyInSurplus() {
        assertFalse(TargetListKind.KEEP.allowsAll());
        assertTrue(TargetListKind.SURPLUS.allowsAll());
        assertFalse(TargetListKind.FILTER.allowsAll());
    }

    @Test
    void amountIsClamped() {
        assertEquals(0, TargetListKind.KEEP.clampAmount(-5));
        assertEquals(TargetListKind.MAX_AMOUNT, TargetListKind.KEEP.clampAmount(Integer.MAX_VALUE));
        assertEquals(128, TargetListKind.SURPLUS.clampAmount(128));
    }

    @Test
    void filterHasNoAmount() {
        assertFalse(TargetListKind.FILTER.hasAmount());
        assertEquals(0, TargetListKind.FILTER.clampAmount(64));
    }

    @Test
    void unknownSavedNameFallsBack() {
        assertEquals(TargetListKind.SURPLUS, TargetListKind.byName("SURPLUS", TargetListKind.KEEP));
        assertEquals(TargetListKind.KEEP, TargetListKind.byName("???", TargetListKind.KEEP));
    }
}
