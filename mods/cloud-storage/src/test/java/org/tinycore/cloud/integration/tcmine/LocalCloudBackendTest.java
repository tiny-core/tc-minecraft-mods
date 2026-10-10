package org.tinycore.cloud.integration.tcmine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LocalCloudBackend}: um arquivo, uma instância por vez. Duas abertas ao mesmo tempo reescreveriam o arquivo
 * uma por cima da outra (perda ou duplicação de itens).
 */
class LocalCloudBackendTest {

    @TempDir
    Path dir;

    @Test
    void secondBackendOnTheSameFileIsRefused() throws Exception {
        Path file = dir.resolve("nuvem.json");
        LocalCloudBackend first = new LocalCloudBackend(file, () -> 60_000L);
        try {
            assertThrows(LocalCloudBackend.InUseException.class, () -> new LocalCloudBackend(file, () -> 60_000L));
        } finally {
            first.close();
        }
    }

    @Test
    void closingReleasesTheFileForTheNextWorld() throws Exception {
        Path file = dir.resolve("nuvem.json");
        new LocalCloudBackend(file, () -> 60_000L).close();
        assertDoesNotThrow(() -> new LocalCloudBackend(file, () -> 60_000L).close());
    }
}
