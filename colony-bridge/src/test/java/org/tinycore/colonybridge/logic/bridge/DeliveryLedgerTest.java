package org.tinycore.colonybridge.logic.bridge;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DeliveryLedger}: coordena várias pontes e o cooldown de reentrega. Um erro aqui faz duas pontes
 * atenderem o mesmo pedido ou um pedido ficar bloqueado para sempre.
 */
class DeliveryLedgerTest {

    private static final String COLONY = "minecraft:overworld#1";
    private static final long BRIDGE_A = 1;
    private static final long BRIDGE_B = 2;

    @Test
    void craftingClaimIsMineForOwnerAndBlockedForOthers() {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markCrafting(COLONY, "r1", BRIDGE_A, 100);
        assertEquals(DeliveryLedger.ClaimState.MINE_CRAFTING, ledger.state(COLONY, "r1", BRIDGE_A));
        assertEquals(DeliveryLedger.ClaimState.OTHER_BRIDGE, ledger.state(COLONY, "r1", BRIDGE_B));
        assertEquals(DeliveryLedger.ClaimState.FREE, ledger.state(COLONY, "r2", BRIDGE_B));
    }

    @Test
    void deliveredBlocksEveryBridge() {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markDelivered(COLONY, "r1", BRIDGE_A, 100);
        assertEquals(DeliveryLedger.ClaimState.DELIVERED, ledger.state(COLONY, "r1", BRIDGE_A));
        assertEquals(DeliveryLedger.ClaimState.DELIVERED, ledger.state(COLONY, "r1", BRIDGE_B));
    }

    @Test
    void onlyTheOwnerCanReleaseOrRenew() {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markCrafting(COLONY, "r1", BRIDGE_A, 100);
        ledger.releaseCrafting(COLONY, "r1", BRIDGE_B); // outra ponte: nada muda
        assertEquals(DeliveryLedger.ClaimState.MINE_CRAFTING, ledger.state(COLONY, "r1", BRIDGE_A));
        ledger.releaseCrafting(COLONY, "r1", BRIDGE_A);
        assertEquals(DeliveryLedger.ClaimState.FREE, ledger.state(COLONY, "r1", BRIDGE_B));
    }

    @Test
    void releaseNeverUndoesADelivery() {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markDelivered(COLONY, "r1", BRIDGE_A, 100);
        ledger.releaseCrafting(COLONY, "r1", BRIDGE_A);
        assertEquals(DeliveryLedger.ClaimState.DELIVERED, ledger.state(COLONY, "r1", BRIDGE_A));
    }

    @Test
    void renewKeepsAClaimAliveThroughExpiry() {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markCrafting(COLONY, "r1", BRIDGE_A, 0);
        ledger.renewCrafting(COLONY, "r1", BRIDGE_A, 900);
        ledger.expire(1500, 1200); // sem a renovação (tempo 0), teria expirado
        assertEquals(DeliveryLedger.ClaimState.MINE_CRAFTING, ledger.state(COLONY, "r1", BRIDGE_A));
        ledger.expire(2200, 1200);
        assertEquals(DeliveryLedger.ClaimState.FREE, ledger.state(COLONY, "r1", BRIDGE_A));
    }

    @Test
    void claimsFromTheFutureExpire() {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markDelivered(COLONY, "r1", BRIDGE_A, 10_000); // ex.: mundo restaurado de backup
        ledger.expire(50, 1200);
        assertEquals(DeliveryLedger.ClaimState.FREE, ledger.state(COLONY, "r1", BRIDGE_A));
    }

    @Test
    void nbtRoundTrip() throws Exception {
        DeliveryLedger ledger = new DeliveryLedger();
        ledger.markDelivered(COLONY, "r1", BRIDGE_A, 100);
        ledger.markCrafting(COLONY, "r2", BRIDGE_B, 200);
        CompoundTag tag = ledger.save(new CompoundTag(), null);

        // load é privado (usado só pela fábrica do SavedData); chamado por reflexão só no teste.
        Method load = DeliveryLedger.class.getDeclaredMethod("load", CompoundTag.class,
                net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        DeliveryLedger loaded = (DeliveryLedger) load.invoke(null, tag, null);
        assertEquals(DeliveryLedger.ClaimState.DELIVERED, loaded.state(COLONY, "r1", BRIDGE_B));
        assertEquals(DeliveryLedger.ClaimState.MINE_CRAFTING, loaded.state(COLONY, "r2", BRIDGE_B));
    }
}
