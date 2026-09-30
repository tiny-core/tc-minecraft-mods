package org.tinycore.colonybridge.logic.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LoaderRule}: quando a área da colônia fica carregada. Erros aqui aparecem no jogo como chunks carregados
 * para sempre (servidor pesado), ou soltos cedo demais (colônia parada com o dono ainda online).
 */
class LoaderRuleTest {

    private static final long HOUR = 3_600_000L;

    private static LoaderState state(boolean powered, boolean grace, boolean member, long remaining) {
        return LoaderRule.state(true, true, true, powered, grace, member, remaining);
    }

    @Test
    void memberOnlineWithPowerLoads() {
        assertEquals(LoaderState.LOADING, state(true, false, true, 0));
    }

    @Test
    void lastMemberLeftStartsCountdown() {
        assertEquals(LoaderState.COUNTDOWN, state(true, false, false, 5 * HOUR));
    }

    @Test
    void countdownOverSleeps() {
        LoaderState state = state(true, false, false, 0);
        assertEquals(LoaderState.SLEEPING, state);
        assertFalse(state.loadsArea());
        assertTrue(state.keepsOwnChunk(), "o bloco precisa perceber quando um membro volta");
    }

    @Test
    void withoutPowerOnlyOwnChunk() {
        LoaderState state = state(false, false, true, 0);
        assertEquals(LoaderState.NO_POWER, state);
        assertFalse(state.loadsArea());
    }

    @Test
    void graceLoadsWithoutPower() {
        assertEquals(LoaderState.LOADING, state(false, true, true, 0));
    }

    @Test
    void offAndAdminReleaseEverything() {
        LoaderState admin = LoaderRule.state(false, true, true, true, false, true, HOUR);
        LoaderState off = LoaderRule.state(true, false, true, true, false, true, HOUR);
        assertEquals(LoaderState.DISABLED_BY_ADMIN, admin);
        assertEquals(LoaderState.OFF, off);
        assertFalse(admin.keepsOwnChunk());
        assertFalse(off.keepsOwnChunk());
    }

    @Test
    void invalidColonyReleasesEverything() {
        LoaderState state = LoaderRule.state(true, true, false, true, false, true, HOUR);
        assertEquals(LoaderState.NO_COLONY, state);
        assertFalse(state.keepsOwnChunk());
    }

    @Test
    void remainingCountsFromLastMember() {
        assertEquals(12 * HOUR, LoaderRule.remainingMillis(1_000, 1_000, 12));
        assertEquals(2 * HOUR, LoaderRule.remainingMillis(0, 10 * HOUR, 12));
        assertEquals(0, LoaderRule.remainingMillis(0, 13 * HOUR, 12));
    }

    @Test
    void zeroHoursReleasesAtOnce() {
        assertEquals(0, LoaderRule.remainingMillis(1_000, 1_000, 0));
    }

    @Test
    void neverSeenCountsAsNow() {
        assertEquals(12 * HOUR, LoaderRule.remainingMillis(-1, 999 * HOUR, 12));
    }

    @Test
    void clockGoingBackDoesNotExtend() {
        assertEquals(12 * HOUR, LoaderRule.remainingMillis(5 * HOUR, 4 * HOUR, 12));
    }

    @Test
    void formatsHoursAndMinutes() {
        long millis = 3 * HOUR + 20 * 60_000;
        assertEquals(3, LoaderRule.hours(millis));
        assertEquals(20, LoaderRule.minutes(millis));
        assertEquals(1, LoaderRule.minutes(1_000), "segundos restantes aparecem como 1 min");
    }
}
