package org.tinycore.cloud.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link LocalCloudFile}: onde a nuvem local fica e a migração do arquivo das versões de teste. */
class LocalCloudFileTest {

    @TempDir
    Path game;

    @Test
    void emptyOrRelativeLivesInTheGameFolder() {
        assertEquals(game.resolve("tccloud-local.json"), LocalCloudFile.resolve("  ", game));
        assertEquals(game.resolve("nuvens/minha.json"), LocalCloudFile.resolve("nuvens/minha.json", game));
    }

    @Test
    void absolutePathIsUsedAsIs(@TempDir Path elsewhere) {
        Path shared = elsewhere.resolve("compartilhada.json").toAbsolutePath();
        assertEquals(shared, LocalCloudFile.resolve(shared.toString(), game));
    }

    @Test
    void legacyTestFileIsRenamedOnce() throws IOException {
        Files.writeString(game.resolve("tccloud-dev-backend.json"), "{}");
        Path file = LocalCloudFile.resolve("", game);
        LocalCloudFile.migrateLegacy(file, game);
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(game.resolve("tccloud-dev-backend.json")));
    }

    @Test
    void customFileNeverTakesTheLegacyOne() throws IOException {
        Files.writeString(game.resolve("tccloud-dev-backend.json"), "{}");
        Path custom = LocalCloudFile.resolve("outra.json", game);
        LocalCloudFile.migrateLegacy(custom, game);
        assertFalse(Files.exists(custom));
        assertTrue(Files.exists(game.resolve("tccloud-dev-backend.json")));
    }
}
