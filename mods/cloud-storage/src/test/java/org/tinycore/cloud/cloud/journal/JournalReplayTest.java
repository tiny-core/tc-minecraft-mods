package org.tinycore.cloud.cloud.journal;

import org.junit.jupiter.api.Test;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudOp;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link JournalReplay}: o que o boot conclui lendo o diário. */
class JournalReplayTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final BalanceKey KEY = new BalanceKey(UUID.randomUUID(), "abc");

    private static JournalRecord batch(long epoch, long seq, long delta) {
        return new JournalRecord.BatchWritten(new Batch(PLAYER, epoch, seq, List.of(new CloudOp(KEY, delta)), Map.of()));
    }

    @Test
    void outboxSaoOsLotesSemConfirmacaoNaOrdem() {
        JournalReplay r = JournalReplay.of(List.of(
                batch(1, 1, -1), batch(1, 2, -2), new JournalRecord.Ack(PLAYER, 1, 1), batch(1, 3, 5),
                new JournalRecord.CleanShutdown(0)));
        assertEquals(List.of(2L, 3L), r.outbox().stream().map(Batch::seq).toList());
        assertEquals(new JournalReplay.SeqPosition(1, 3), r.lastSealed().get(PLAYER));
        assertFalse(r.uncleanShutdown());
        assertTrue(r.doubtful().isEmpty());
    }

    @Test
    void epocaNovaVenceNaUltimaPosicao() {
        JournalReplay r = JournalReplay.of(List.of(batch(2, 9, -1), batch(3, 1, -1)));
        assertEquals(new JournalReplay.SeqPosition(3, 1), r.lastSealed().get(PLAYER));
    }

    @Test
    void crashListaCreditosPendentesEDebitosDepoisDoUltimoSave() {
        JournalReplay r = JournalReplay.of(List.of(
                batch(1, 1, -10),                   // antes do save: o mundo já gravou
                new JournalRecord.SaveMark(0),
                batch(1, 2, -3),                    // depois do save: em dúvida
                batch(1, 3, 20),                    // crédito durável: não é dúvida
                new JournalRecord.PendingCredits(PLAYER, Map.of(KEY, 4L))));
        assertTrue(r.uncleanShutdown());
        assertEquals(List.of(
                new DoubtfulOperation(PLAYER, KEY, DoubtfulOperation.Kind.PENDING_CREDIT, 4),
                new DoubtfulOperation(PLAYER, KEY, DoubtfulOperation.Kind.RECENT_DEBIT, 3)), r.doubtful());
    }

    @Test
    void registroDePendentesMaisRecenteSubstituiOAnterior() {
        JournalReplay r = JournalReplay.of(List.of(
                new JournalRecord.PendingCredits(PLAYER, Map.of(KEY, 4L)),
                new JournalRecord.PendingCredits(PLAYER, Map.of())));
        assertTrue(r.doubtful().isEmpty());
    }

    @Test
    void compactacaoGuardaSoOQueAindaImporta() {
        JournalReplay r = JournalReplay.of(List.of(
                batch(1, 1, -1), new JournalRecord.Ack(PLAYER, 1, 1), batch(1, 2, -1),
                new JournalRecord.SaveMark(0), new JournalRecord.PendingCredits(PLAYER, Map.of(KEY, 2L))));
        List<JournalRecord> kept = r.compacted();
        assertEquals(2, kept.size());
        assertEquals(2, ((JournalRecord.BatchWritten) kept.get(0)).batch().seq());
        assertTrue(kept.get(1) instanceof JournalRecord.PendingCredits);
    }
}
