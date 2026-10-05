package org.tinycore.cloud.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.integration.tcmine.CloudBackend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link DoubtfulOutbox} e {@link SuspectCollector}: o que não pode se perder até o TCMine confirmar. */
class DoubtfulOutboxTest {

    @TempDir
    Path dir;

    @Test
    void relatorioSobreviveAteSerConfirmado() throws IOException {
        DoubtfulOperation op = new DoubtfulOperation(UUID.randomUUID(), new BalanceKey(UUID.randomUUID(), "f".repeat(64)),
                DoubtfulOperation.Kind.RECENT_DEBIT, 7);
        DoubtfulOutbox outbox = new DoubtfulOutbox(dir);
        String id = outbox.add(List.of(op)).reportId();

        // "Reinício": outro objeto lendo a mesma pasta.
        List<DoubtfulOutbox.Report> pending = new DoubtfulOutbox(dir).pending();
        assertEquals(1, pending.size());
        assertEquals(id, pending.getFirst().reportId());
        assertEquals(List.of(op), pending.getFirst().operations());

        outbox.confirm(pending.getFirst());
        assertTrue(outbox.pending().isEmpty());
    }

    @Test
    void suspeitosSomamTentativasEVoltamSeOEnvioFalhar() {
        SuspectCollector collector = new SuspectCollector();
        collector.record("mod:mochila", "uuid", true);  // simulação do AE2: entra na fila, não conta tentativa
        collector.record("mod:mochila", "uuid", false);
        collector.record("mod:mochila", "uuid", false);

        List<CloudBackend.SuspectReport> batch = collector.drain();
        assertEquals(List.of(new CloudBackend.SuspectReport("mod:mochila", "uuid", 2)), batch);
        assertTrue(collector.drain().isEmpty());

        collector.restore(batch);
        collector.record("mod:mochila", "uuid", false);
        assertEquals(3, collector.drain().getFirst().attempts());
    }
}
