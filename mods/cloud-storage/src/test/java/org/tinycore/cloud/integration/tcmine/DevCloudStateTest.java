package org.tinycore.cloud.integration.tcmine;

import org.junit.jupiter.api.Test;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudOp;
import org.tinycore.cloud.item.EncodedItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * {@link DevCloudState}: as regras que o TCMine real também terá (lease com época, lote idempotente, saldo
 * nunca negativo, quarentena). O backend de desenvolvimento é o primeiro "contrato" do protocolo.
 */
class DevCloudStateTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final long TTL = 60_000;
    private static final EncodedItem DIAMOND = new EncodedItem("fp-diamond", "minecraft:diamond", "Diamante", new byte[]{1});

    private static CloudBackend.LeaseResult.Granted grant(DevCloudState state, String holder, long now) {
        return assertInstanceOf(CloudBackend.LeaseResult.Granted.class, state.acquire(PLAYER, holder, now, TTL));
    }

    private static Batch batch(UUID channel, long epoch, long seq, long delta, long expected) {
        BalanceKey key = new BalanceKey(channel, DIAMOND.fingerprint());
        return new Batch(PLAYER, epoch, seq, List.of(new CloudOp(key, delta)), Map.of(key, expected));
    }

    @Test
    void primeiroAcessoCriaOCanalPrincipal() {
        CloudBackend.LeaseResult.Granted g = grant(new DevCloudState(), "mundoA", 0);
        assertEquals(1, g.epoch());
        assertEquals(1, g.channels().size());
        assertEquals(DevCloudState.DEFAULT_CHANNEL, g.channels().getFirst().name());
    }

    @Test
    void outroMundoNaoPegaOCanalAteExpirarOuLiberar() {
        DevCloudState state = new DevCloudState();
        grant(state, "mundoA", 0);
        assertInstanceOf(CloudBackend.LeaseResult.Busy.class, state.acquire(PLAYER, "mundoB", 1000, TTL));
        assertEquals(2, grant(state, "mundoB", TTL + 1).epoch(), "expirou: nova época");
    }

    @Test
    void releaseSoComTodosOsLotesConfirmados() {
        DevCloudState state = new DevCloudState();
        CloudBackend.LeaseResult.Granted g = grant(state, "mundoA", 0);
        UUID channel = g.channels().getFirst().id();
        state.submit(batch(channel, 1, 1, 5, 5), List.of(DIAMOND), "mundoA");
        state.release(PLAYER, 1, 0, "mundoA"); // lastSeq errado: não libera
        assertInstanceOf(CloudBackend.LeaseResult.Busy.class, state.acquire(PLAYER, "mundoB", 1, TTL));
        state.release(PLAYER, 1, 1, "mundoA");
        CloudBackend.LeaseResult.Granted b = grant(state, "mundoB", 2);
        assertEquals(5L, b.channels().getFirst().amounts().get(DIAMOND.fingerprint()));
        assertEquals(DIAMOND, b.channels().getFirst().items().get(DIAMOND.fingerprint()));
    }

    @Test
    void reenvioEhDuplicadoENaoAplicaDuasVezes() {
        DevCloudState state = new DevCloudState();
        UUID channel = grant(state, "mundoA", 0).channels().getFirst().id();
        Batch b = batch(channel, 1, 1, 10, 10);
        assertEquals(CloudBackend.BatchResult.APPLIED, state.submit(b, List.of(DIAMOND), "mundoA"));
        assertEquals(CloudBackend.BatchResult.DUPLICATE, state.submit(b, List.of(DIAMOND), "mundoA"));
        assertEquals(10L, state.players.get(PLAYER.toString()).channels.getFirst().amounts.get(DIAMOND.fingerprint()));
    }

    @Test
    void epocaVelhaSaldoNegativoEDivergenciaVaoParaQuarentena() {
        DevCloudState state = new DevCloudState();
        UUID channel = grant(state, "mundoA", 0).channels().getFirst().id();
        state.submit(batch(channel, 1, 1, 10, 10), List.of(DIAMOND), "mundoA");
        assertEquals(CloudBackend.BatchResult.QUARANTINED, state.submit(batch(channel, 1, 2, -11, -1), List.of(), "mundoA"));
        assertEquals(CloudBackend.BatchResult.QUARANTINED, state.submit(batch(channel, 1, 2, -1, 8), List.of(), "mundoA"));
        grant(state, "mundoA", 1); // novo lease: época 2
        assertEquals(CloudBackend.BatchResult.QUARANTINED, state.submit(batch(channel, 1, 2, -1, 9), List.of(), "mundoA"));
        assertEquals(3, state.quarantine.size());
        assertEquals(10L, state.players.get(PLAYER.toString()).channels.getFirst().amounts.get(DIAMOND.fingerprint()));
    }

    @Test
    void buracoNaSequenciaVaiParaQuarentena() {
        DevCloudState state = new DevCloudState();
        UUID channel = grant(state, "mundoA", 0).channels().getFirst().id();
        assertEquals(CloudBackend.BatchResult.QUARANTINED, state.submit(batch(channel, 1, 2, 1, 1), List.of(DIAMOND), "mundoA"));
    }
}
