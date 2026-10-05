package org.tinycore.cloud.integration.tcmine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.TcCloud;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Onde o servidor de jogo acha a nuvem do TCMine (URL + chave do servidor), em ordem:
 * <ol>
 *   <li>variáveis de ambiente {@code TCMINE_CLOUD_URL}/{@code TCMINE_CLOUD_KEY} (servidor configurado à mão);</li>
 *   <li>{@code tccloud-server.json} na pasta do servidor — o TCMine grava este arquivo a cada start de um
 *       servidor que ele gerencia, com uma chave nova (a anterior deixa de valer).</li>
 * </ol>
 * A chave NUNCA vem de config do mod: a config SERVER é sincronizada para os clientes e o arquivo de config
 * costuma ir junto do modpack.
 *
 * @param source de onde veio (para o log, sem mostrar a chave)
 */
public record CloudCredentials(@NotNull URI url, @NotNull String key, @NotNull String source) {

    public static final String ENV_URL = "TCMINE_CLOUD_URL";
    public static final String ENV_KEY = "TCMINE_CLOUD_KEY";
    public static final String FILE_NAME = "tccloud-server.json";

    /**
     * @param env       leitor de variáveis de ambiente ({@code System::getenv} no jogo; injetável nos testes)
     * @param serverDir pasta do servidor ({@code /data} no container do TCMine)
     * @return {@code null} se não há nuvem configurada (ou a configuração é inválida, com aviso no log)
     */
    public static @Nullable CloudCredentials find(@NotNull Function<String, String> env, @NotNull Path serverDir) {
        String url = env.apply(ENV_URL);
        String key = env.apply(ENV_KEY);
        if (url != null || key != null) return of(url, key, "variáveis de ambiente");

        Path file = serverDir.resolve(FILE_NAME);
        if (!Files.exists(file)) return null;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return of(text(json, "url"), text(json, "key"), FILE_NAME);
        } catch (IOException | RuntimeException e) {
            TcCloud.LOG.warn("Nuvem: {} ilegível ({}); nuvem desligada.", file, e.toString());
            return null;
        }
    }

    private static @Nullable CloudCredentials of(@Nullable String url, @Nullable String key, String source) {
        if (url == null || url.isBlank() || key == null || key.isBlank()) {
            TcCloud.LOG.warn("Nuvem: URL ou chave faltando em {}; nuvem desligada.", source);
            return null;
        }
        try {
            URI uri = URI.create(url.trim().replaceAll("/+$", ""));
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
                throw new IllegalArgumentException("esquema " + uri.getScheme());
            }
            return new CloudCredentials(uri, key.trim(), source);
        } catch (IllegalArgumentException e) {
            TcCloud.LOG.warn("Nuvem: URL inválida em {} ({}); nuvem desligada.", source, e.getMessage());
            return null;
        }
    }

    private static @Nullable String text(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : null;
    }

    /** Nunca imprime a chave: só o prefixo não secreto ({@code tcs_<prefixo>_…}). */
    @Override
    public @NotNull String toString() {
        int second = key.indexOf('_', 4);
        String visible = second > 0 ? key.substring(0, second) + "_…" : "…";
        return "CloudCredentials[" + url + ", " + visible + ", " + source + "]";
    }
}
