package org.tinycore.cloud.cloud.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudOp;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link JournalFile}/{@link JournalCodec}: ida e volta, e sobrevivência a gravação interrompida. */
class JournalFileTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final BalanceKey KEY = new BalanceKey(UUID.randomUUID(), "abc123");

    @TempDir
    Path dir;

    private static List<JournalRecord> sample() {
        Batch batch = new Batch(PLAYER, 3, 1, List.of(new CloudOp(KEY, -5)), Map.of(KEY, 95L));
        return List.of(
                new JournalRecord.BatchWritten(batch),
                new JournalRecord.Ack(PLAYER, 3, 1),
                new JournalRecord.SaveMark(123L),
                new JournalRecord.PendingCredits(PLAYER, Map.of(KEY, 7L)),
                new JournalRecord.CleanShutdown(456L));
    }

    @Test
    void idaEVolta() throws IOException {
        JournalFile file = new JournalFile(dir.resolve("tccloud/journal.bin"));
        file.append(sample().subList(0, 2), true);
        file.append(sample().subList(2, 5), false);
        assertEquals(sample(), file.readAll());
    }

    @Test
    void arquivoInexistenteEhDiarioVazio() throws IOException {
        assertEquals(List.of(), new JournalFile(dir.resolve("nada.bin")).readAll());
    }

    @Test
    void quadroCortadoNoFimEhDescartado() throws IOException {
        JournalFile file = new JournalFile(dir.resolve("journal.bin"));
        file.append(sample(), true);
        long size = Files.size(file.path());
        try (RandomAccessFile raf = new RandomAccessFile(file.path().toFile(), "rw")) {
            raf.setLength(size - 3); // crash no meio da gravação do último quadro
        }
        assertEquals(sample().subList(0, 4), file.readAll());
    }

    @Test
    void crcErradoEncerraALeitura() throws IOException {
        JournalFile file = new JournalFile(dir.resolve("journal.bin"));
        file.append(sample().subList(0, 1), true);
        long firstEnd = Files.size(file.path());
        file.append(sample().subList(1, 3), true);
        try (RandomAccessFile raf = new RandomAccessFile(file.path().toFile(), "rw")) {
            long pos = firstEnd + 6; // um byte dentro dos dados do 2º quadro
            raf.seek(pos);
            int original = raf.read();
            raf.seek(pos);
            raf.write(original ^ 0xFF);
        }
        assertEquals(sample().subList(0, 1), file.readAll());
    }

    @Test
    void reescreverSubstituiTudo() throws IOException {
        JournalFile file = new JournalFile(dir.resolve("journal.bin"));
        file.append(sample(), true);
        file.rewrite(sample().subList(2, 3));
        assertEquals(sample().subList(2, 3), file.readAll());
    }
}
