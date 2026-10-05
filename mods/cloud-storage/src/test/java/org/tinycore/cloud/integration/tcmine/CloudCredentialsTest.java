package org.tinycore.cloud.integration.tcmine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** {@link CloudCredentials}: variáveis de ambiente primeiro, depois o arquivo que o TCMine grava. */
class CloudCredentialsTest {

    @TempDir
    Path dir;

    private static String env(Map<String, String> values, String name) {
        return values.get(name);
    }

    @Test
    void semNadaNaoHaNuvem() {
        assertNull(CloudCredentials.find(n -> null, dir));
    }

    @Test
    void arquivoDoTcmine() throws IOException {
        Files.writeString(dir.resolve(CloudCredentials.FILE_NAME), "{\"url\":\"https://tcmine.exemplo.com/\",\"key\":\"tcs_x\"}");

        CloudCredentials c = CloudCredentials.find(n -> null, dir);

        assertEquals("https://tcmine.exemplo.com", c.url().toString());
        assertEquals("tcs_x", c.key());
        assertEquals(CloudCredentials.FILE_NAME, c.source());
    }

    @Test
    void variaveisDeAmbienteVencemOArquivo() throws IOException {
        Files.writeString(dir.resolve(CloudCredentials.FILE_NAME), "{\"url\":\"https://arquivo\",\"key\":\"a\"}");
        Map<String, String> vars = Map.of(CloudCredentials.ENV_URL, "http://env:8080", CloudCredentials.ENV_KEY, "b");

        CloudCredentials c = CloudCredentials.find(n -> env(vars, n), dir);

        assertEquals("http://env:8080", c.url().toString());
        assertEquals("b", c.key());
    }

    @Test
    void configuracaoIncompletaOuInvalidaDesligaANuvem() throws IOException {
        assertNull(CloudCredentials.find(n -> CloudCredentials.ENV_URL.equals(n) ? "https://x" : null, dir));
        Files.writeString(dir.resolve(CloudCredentials.FILE_NAME), "{\"url\":\"ftp://x\",\"key\":\"k\"}");
        assertNull(CloudCredentials.find(n -> null, dir));
        Files.writeString(dir.resolve(CloudCredentials.FILE_NAME), "{ não é json");
        assertNull(CloudCredentials.find(n -> null, dir));
    }
}
