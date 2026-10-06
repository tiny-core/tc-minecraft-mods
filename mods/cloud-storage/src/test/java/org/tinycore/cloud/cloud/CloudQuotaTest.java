package org.tinycore.cloud.cloud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link CloudQuota#fill}: a barra de cota da tela do Cloud Link. */
class CloudQuotaTest {

    @Test
    void unlimitedQuotaIsEmpty() {
        assertEquals(0, CloudQuota.UNLIMITED.fill(500, 1_000_000));
        assertFalse(CloudQuota.UNLIMITED.limitsTypes());
        assertFalse(CloudQuota.UNLIMITED.limitsTotal());
    }

    @Test
    void fillIsTheTightestLimit() {
        CloudQuota quota = new CloudQuota(10, 1000);
        assertEquals(0.5, quota.fill(5, 100), 1e-9, "tipos 50 % > total 10 %");
        assertEquals(0.9, quota.fill(1, 900), 1e-9, "total 90 % > tipos 10 %");
    }

    @Test
    void fillIgnoresTheUnlimitedSide() {
        CloudQuota onlyTotal = new CloudQuota(Integer.MAX_VALUE, 200);
        assertTrue(onlyTotal.limitsTotal());
        assertEquals(0.25, onlyTotal.fill(1000, 50), 1e-9);
    }

    @Test
    void fillIsClampedAndZeroLimitIsFull() {
        assertEquals(1, new CloudQuota(10, 100).fill(3, 500), 1e-9);
        assertEquals(1, new CloudQuota(0, Long.MAX_VALUE).fill(0, 0), 1e-9);
    }
}
