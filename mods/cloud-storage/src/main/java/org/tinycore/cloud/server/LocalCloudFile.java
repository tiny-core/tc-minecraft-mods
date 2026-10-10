package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.TcCloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Onde fica o arquivo da nuvem local ({@code localCloudFile}). Regra pura (testada), separada do
 * {@link CloudBackends}:
 * <ul>
 *   <li>vazio ou inválido = o padrão, {@value #DEFAULT_NAME} na pasta do jogo/servidor;</li>
 *   <li>relativo = dentro da pasta do jogo/servidor (uma nuvem por instância);</li>
 *   <li>absoluto = esse arquivo (a mesma nuvem para várias instâncias deste computador).</li>
 * </ul>
 * Também migra o arquivo das versões de teste ({@value #LEGACY_NAME}) para o nome novo, uma vez.
 */
public final class LocalCloudFile {

    public static final String DEFAULT_NAME = "tccloud-local.json";
    /** Nome do arquivo quando a nuvem local se chamava "backend de desenvolvimento". */
    static final String LEGACY_NAME = "tccloud-dev-backend.json";

    private LocalCloudFile() {}

    public static @NotNull Path resolve(@NotNull String configured, @NotNull Path gameDir) {
        String value = configured.strip();
        if (value.isEmpty()) return gameDir.resolve(DEFAULT_NAME);
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? path : gameDir.resolve(path);
        } catch (InvalidPathException e) {
            TcCloud.LOG.warn("Nuvem local: localCloudFile inválido ({}); usando {}.", value, DEFAULT_NAME);
            return gameDir.resolve(DEFAULT_NAME);
        }
    }

    /**
     * Arquivo novo ainda não existe e o antigo existe na pasta do jogo? Renomeia (os canais dos testes continuam).
     * Só para o caminho padrão: um caminho escolhido pelo jogador nunca é sobrescrito.
     */
    static void migrateLegacy(@NotNull Path file, @NotNull Path gameDir) {
        Path legacy = gameDir.resolve(LEGACY_NAME);
        if (!file.equals(gameDir.resolve(DEFAULT_NAME)) || Files.exists(file) || !Files.exists(legacy)) return;
        try {
            Files.move(legacy, file);
            TcCloud.LOG.info("Nuvem local: {} renomeado para {}.", LEGACY_NAME, DEFAULT_NAME);
        } catch (IOException e) {
            TcCloud.LOG.warn("Nuvem local: não consegui renomear {}: {}", legacy, e.toString());
        }
    }
}
