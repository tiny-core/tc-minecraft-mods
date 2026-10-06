package org.tinycore.cloud;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do TC Cloud Storage. É do tipo <b>COMMON</b> ({@code config/tccloud-common.toml}) de propósito: a
 * config do tipo SERVER é sincronizada para os clientes, e nada daqui deve ir para os jogadores. A URL e a
 * chave do TCMine NUNCA ficam aqui; chegam por variável de ambiente (fase 4).
 */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEV_FILE_BACKEND = B
            .comment("Liga o backend de desenvolvimento: a \"nuvem\" vira o arquivo tccloud-dev-backend.json na pasta",
                    "do jogo/servidor (compartilhado entre mundos). Só para testes; em produção a nuvem é o TCMine.")
            .define("devFileBackend", false);

    public static final ModConfigSpec.IntValue DEV_QUOTA_MAX_TYPES = B
            .comment("Cota da nuvem de teste (devFileBackend): máximo de tipos de item por canal. 0 = sem limite.",
                    "Com o TCMine, a cota vem do painel e isto é ignorado.")
            .defineInRange("devQuotaMaxTypes", 0, 0, 1_000_000);

    public static final ModConfigSpec.LongValue DEV_QUOTA_MAX_TOTAL = B
            .comment("Cota da nuvem de teste (devFileBackend): soma máxima das quantidades por canal. 0 = sem limite.")
            .defineInRange("devQuotaMaxTotal", 0L, 0L, Long.MAX_VALUE);

    public static final ModConfigSpec.IntValue DEV_LEASE_TTL_SECONDS = B
            .comment("Backend de desenvolvimento: segundos sem heartbeat até o lease de um jogador expirar.")
            .defineInRange("devLeaseTtlSeconds", 90, 30, 3600);

    public static final ModConfigSpec.IntValue HEARTBEAT_SECONDS = B
            .comment("Intervalo do heartbeat dos leases ao backend.")
            .defineInRange("heartbeatSeconds", 20, 5, 600);

    public static final ModConfigSpec.IntValue LOGOUT_SAVE_COOLDOWN_SECONDS = B
            .comment("Janela mínima entre dois saves com flush disparados por logout (vários logouts juntos = um save).",
                    "Até esse save, o canal do jogador fica \"sincronizando\" para os outros servidores.")
            .defineInRange("logoutSaveCooldownSeconds", 30, 5, 600);

    public static final ModConfigSpec.IntValue SEND_RETRY_SECONDS = B
            .comment("Espera antes de reenviar um lote que falhou (backend fora do ar).")
            .defineInRange("sendRetrySeconds", 10, 1, 600);

    public static final ModConfigSpec.IntValue MAX_ITEM_BYTES = B
            .comment("Tamanho máximo de um item codificado, até o backend mandar o valor da nuvem.")
            .defineInRange("maxItemBytes", 8192, 512, 1 << 20);

    public static final ModConfigSpec.IntValue CATALOG_CACHE_SIZE = B
            .comment("Quantos tipos de item codificados ficam em cache (evita recodificar a cada chamada do AE2).")
            .defineInRange("catalogCacheSize", 4096, 256, 1 << 16);

    public static final ModConfigSpec.IntValue JOURNAL_COMPACT_KB = B
            .comment("Tamanho do diário (KB) a partir do qual ele é compactado no save.")
            .defineInRange("journalCompactKb", 1024, 64, 1 << 20);

    public static final ModConfigSpec.IntValue MAX_SYNC_ENTRIES = B
            .comment("Máximo de tipos de item enviados para a tela do TC Cloud Link (protege contra pacotes gigantes).")
            .defineInRange("maxSyncEntries", 2000, 100, 20000);

    public static final ModConfigSpec.DoubleValue LINK_IDLE_POWER = B
            .comment("Consumo parado do TC Cloud Link na rede AE2 (AE/t).")
            .defineInRange("linkIdlePower", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
