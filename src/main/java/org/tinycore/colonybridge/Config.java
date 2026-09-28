package org.tinycore.colonybridge;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue CYCLE_TICKS = B
            .comment("Intervalo, em ticks, entre ciclos de leitura dos pedidos (20 = 1s).")
            .defineInRange("cycleTicks", 100, 20, 6000);

    public static final ModConfigSpec.IntValue MAX_REQUESTS_PER_CYCLE = B
            .comment("Máximo de pedidos tratados por ciclo (limita custo por tick).")
            .defineInRange("maxRequestsPerCycle", 8, 1, 128);

    public static final ModConfigSpec.IntValue MAX_CRAFT_PER_REQUEST = B
            .comment("Quantidade máxima pedida ao autocrafting por pedido.")
            .defineInRange("maxCraftPerRequest", 64, 1, 4096);

    public static final ModConfigSpec.IntValue REDELIVERY_COOLDOWN_TICKS = B
            .comment("Tempo mínimo antes de voltar a entregar para o mesmo pedido.")
            .defineInRange("redeliveryCooldownTicks", 1200, 100, 72000);

    public static final ModConfigSpec.IntValue CRAFT_FAIL_COOLDOWN_TICKS = B
            .comment("Espera antes de tentar de novo um craft que falhou (faltam materiais, sem CPU...).")
            .defineInRange("craftFailCooldownTicks", 2400, 100, 72000);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CRAFT_BLACKLIST = B
            .comment("IDs de itens que nunca devem ser craftados automaticamente (ex: \"minecraft:diamond_block\").")
            .defineListAllowEmpty("craftBlacklist", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.IntValue STATS_BUCKET_TICKS = B
            .comment("Tamanho de cada bloco de tempo das estatísticas, em ticks (6000 = 5 min).",
                    "Mudar este valor zera as estatísticas salvas.")
            .defineInRange("statsBucketTicks", 6000, 1200, 72000);

    public static final ModConfigSpec.IntValue STATS_BUCKETS = B
            .comment("Quantos blocos de tempo as estatísticas guardam (288 × 5 min = 24 h).",
                    "Mudar este valor zera as estatísticas salvas.")
            .defineInRange("statsBuckets", 288, 24, 2016);

    public static final ModConfigSpec.IntValue MONITOR_MAX_WIDTH = B
            .comment("Largura máxima (em blocos) de uma tela formada por monitores.")
            .defineInRange("monitorMaxWidth", 8, 1, 16);

    public static final ModConfigSpec.IntValue MONITOR_MAX_HEIGHT = B
            .comment("Altura máxima (em blocos) de uma tela formada por monitores.")
            .defineInRange("monitorMaxHeight", 6, 1, 16);

    public static final ModConfigSpec.IntValue MONITOR_LINK_RANGE = B
            .comment("Distância máxima (em blocos) entre a tela de monitor e a ponte ligada pelo cartão.")
            .defineInRange("monitorLinkRange", 64, 8, 256);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
