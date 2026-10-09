package org.tinycore.cloud.block;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link NetworkAccess}: o que a rede AE2 pode fazer com o canal em cada modo do Cloud Link. */
class NetworkAccessTest {

    @Test
    void extractOnlySeesAndTakesButNeverStores() {
        assertTrue(NetworkAccess.EXTRACT_ONLY.mounts());
        assertTrue(NetworkAccess.EXTRACT_ONLY.allowsExtract());
        assertFalse(NetworkAccess.EXTRACT_ONLY.allowsInsert());
    }

    @Test
    void depositOnlyStoresButCannotSeeOrTake() {
        assertTrue(NetworkAccess.DEPOSIT_ONLY.allowsInsert());
        assertFalse(NetworkAccess.DEPOSIT_ONLY.allowsExtract());
    }

    @Test
    void terminalOnlyIsInvisibleToTheNetwork() {
        assertFalse(NetworkAccess.TERMINAL_ONLY.mounts());
        assertFalse(NetworkAccess.TERMINAL_ONLY.allowsInsert());
        assertFalse(NetworkAccess.TERMINAL_ONLY.allowsExtract());
    }

    @Test
    void buttonCyclesThroughEveryModeOnce() {
        Set<NetworkAccess> seen = EnumSet.noneOf(NetworkAccess.class);
        NetworkAccess mode = NetworkAccess.TERMINAL_ONLY;
        for (int i = 0; i < NetworkAccess.values().length; i++) {
            seen.add(mode);
            mode = mode.next();
        }
        assertEquals(EnumSet.allOf(NetworkAccess.class), seen);
        assertEquals(NetworkAccess.TERMINAL_ONLY, mode);
    }

    @Test
    void savedOrdinalsKeepTheirMeaning() {
        assertEquals(NetworkAccess.FULL, NetworkAccess.byId(2), "blocos já salvos com FULL");
        assertEquals(NetworkAccess.EXTRACT_ONLY, NetworkAccess.byId(3));
    }
}
