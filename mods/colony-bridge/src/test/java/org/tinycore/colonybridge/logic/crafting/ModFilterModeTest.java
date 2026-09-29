package org.tinycore.colonybridge.logic.crafting;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ModFilterMode}: quais mods cada modo deixa escolher e leitura segura de números vindos de fora. */
class ModFilterModeTest {

    private static final Set<String> MARKED = Set.of("minecraft");

    @Test
    void allowsFollowsTheMode() {
        assertTrue(ModFilterMode.ALL.allows("mekanism", MARKED));
        assertTrue(ModFilterMode.ONLY.allows("minecraft", MARKED));
        assertFalse(ModFilterMode.ONLY.allows("mekanism", MARKED));
        assertFalse(ModFilterMode.EXCEPT.allows("minecraft", MARKED));
        assertTrue(ModFilterMode.EXCEPT.allows("mekanism", MARKED));
        assertTrue(ModFilterMode.PREFER.allows("mekanism", MARKED), "PREFER só ordena, não exclui");
    }

    @Test
    void invalidIdFallsBackToAll() {
        assertEquals(ModFilterMode.ALL, ModFilterMode.byId(-1));
        assertEquals(ModFilterMode.ALL, ModFilterMode.byId(99));
        assertEquals(ModFilterMode.PREFER, ModFilterMode.byId(3));
    }

    @Test
    void nextCyclesThroughAllModes() {
        assertEquals(ModFilterMode.ONLY, ModFilterMode.ALL.next());
        assertEquals(ModFilterMode.ALL, ModFilterMode.PREFER.next());
    }
}
