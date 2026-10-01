package org.tinycore.cloud.cloud.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link Checkpoint}: JSON que o TCMine lê de dentro do zip de backup. */
class CheckpointTest {

    @TempDir
    Path dir;

    @Test
    void idaEVoltaEMesclaPelaPosicaoMaisNova() throws IOException {
        UUID player = UUID.randomUUID();
        Checkpoint cp = Checkpoint.fresh()
                .withLastSealed(Map.of(player, new JournalReplay.SeqPosition(2, 5)))
                .withLastSealed(Map.of(player, new JournalReplay.SeqPosition(2, 3)));
        Path file = dir.resolve("tccloud/checkpoint.json");
        cp.write(file);
        Checkpoint read = Checkpoint.read(file);
        assertEquals(cp, read);
        assertEquals(new JournalReplay.SeqPosition(2, 5), read.lastSealed().get(player));
    }

    @Test
    void ilegivelOuAusenteDevolveNull() throws IOException {
        assertNull(Checkpoint.read(dir.resolve("nada.json")));
        Path bad = dir.resolve("bad.json");
        Files.writeString(bad, "{ isto não é json");
        assertNull(Checkpoint.read(bad));
    }
}
