package org.tinycore.colonybridge;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;

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

    public static final ModConfigSpec.BooleanValue TAG_CRAFTING = B
            .comment("Craftar para pedidos por tag/ferramenta/comida quando a rede não tem nenhum item que sirva.",
                    "A ponte escolhe um item craftável que o pedido aceite (ver tagCraftPreference).")
            .define("tagCrafting", true);

    public static final ModConfigSpec.EnumValue<CraftPreference> TAG_CRAFT_PREFERENCE = B
            .comment("Padrão para pontes em \"Padrão do servidor\": qual item craftar quando vários servem:",
                    "CHEAPEST = menor custo estimado pelas receitas do AE2;",
                    "MOST_EXPENSIVE = maior custo estimado;",
                    "LIST = na ordem de tagCraftPreferredItems (os demais depois, do mais barato ao mais caro).")
            .defineEnum("tagCraftPreference", CraftPreference.CHEAPEST);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAG_CRAFT_PREFERRED_ITEMS = B
            .comment("Itens preferidos, em ordem, para o modo LIST quando a ponte não tem itens preferidos próprios",
                    "(ex: [\"minecraft:bread\", \"minecraft:stone_pickaxe\"]).")
            .defineListAllowEmpty("tagCraftPreferredItems", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue TAG_CRAFT_VANILLA_ONLY = B
            .comment("Regra do servidor: só escolher itens do Minecraft vanilla (namespace \"minecraft\") para pedidos",
                    "por tag. Vale para todas as pontes, que não podem passar por cima dela.")
            .define("tagCraftVanillaOnly", false);

    public static final ModConfigSpec.IntValue TAG_CRAFT_MAX_CANDIDATES = B
            .comment("Máximo de itens craftáveis comparados por pedido (limita o custo em redes enormes).")
            .defineInRange("tagCraftMaxCandidates", 64, 1, 1024);

    public static final ModConfigSpec.IntValue CRAFT_COST_DEPTH = B
            .comment("Quantos níveis de receita seguir ao estimar o custo de um item (mais = mais preciso e mais caro).")
            .defineInRange("craftCostDepth", 4, 1, 8);

    public static final ModConfigSpec.IntValue STATS_BUCKET_TICKS = B
            .comment("Tamanho de cada bloco de tempo das estatísticas, em ticks (6000 = 5 min).",
                    "Mudar este valor zera as estatísticas salvas.")
            .defineInRange("statsBucketTicks", 6000, 1200, 72000);

    public static final ModConfigSpec.IntValue STATS_BUCKETS = B
            .comment("Quantos blocos de tempo as estatísticas guardam (288 × 5 min = 24 h).",
                    "Mudar este valor zera as estatísticas salvas.")
            .defineInRange("statsBuckets", 288, 24, 2016);

    public static final ModConfigSpec.IntValue SUPPLY_MAX_PER_CYCLE = B
            .comment("Máximo de itens que cada linha do bloco de abastecimento move por ciclo.")
            .defineInRange("supplyMaxPerCycle", 64, 1, 4096);

    public static final ModConfigSpec.IntValue LIST_MAX_LINES = B
            .comment("Máximo de linhas em cada lista (Manter e Excedente do Abastecedor, filtro da Ponte).",
                    "Baixar o valor corta as linhas do fim das listas existentes.")
            .defineInRange("listMaxLines", 32, 1, 64);

    public static final ModConfigSpec.IntValue TABLET_CAPACITY = B
            .comment("Bateria do TC Colony Tablet, em FE.")
            .defineInRange("tabletCapacity", 100_000, 1_000, 10_000_000);

    public static final ModConfigSpec.IntValue TABLET_USE_PER_TICK = B
            .comment("FE que o tablet gasta por tick enquanto uma tela está aberta por ele (0 = não gasta).")
            .defineInRange("tabletUsePerTick", 5, 0, 10_000);

    public static final ModConfigSpec.IntValue TABLET_CHARGE_RATE = B
            .comment("FE por tick que o carregador da Ponte põe no tablet (a energia sai da rede ME, convertida do AE).")
            .defineInRange("tabletChargeRate", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.BooleanValue CHUNK_LOADER_ENABLED = B
            .comment("Liga o TC Colony Chunk Loader no servidor. false = todos os loaders soltam os chunks e não carregam nada.")
            .define("chunkLoaderEnabled", true);

    public static final ModConfigSpec.IntValue CHUNK_LOADER_MAX_CHUNKS = B
            .comment("Máximo de chunks que um Chunk Loader carrega (os mais perto do centro da colônia primeiro).",
                    "Atenção: o loader passa por cima do limite de chunks forçados do FTB Chunks.")
            .defineInRange("chunkLoaderMaxChunks", 144, 1, 1024);

    public static final ModConfigSpec.DoubleValue CHUNK_LOADER_POWER_PER_CHUNK = B
            .comment("Energia AE por tick que o Chunk Loader gasta por chunk carregado (inclui o chunk do próprio bloco).")
            .defineInRange("chunkLoaderPowerPerChunk", 32.0, 0.0, 100_000.0);

    public static final ModConfigSpec.DoubleValue CHUNK_LOADER_OFFLINE_HOURS = B
            .comment("Horas (tempo real) que a área continua carregada depois que o último membro da colônia sai.",
                    "0 = solta assim que o último sai. Aceita frações (0.05 = 3 minutos).")
            .defineInRange("chunkLoaderOfflineHours", 12.0, 0.0, 720.0);

    public static final ModConfigSpec.IntValue CHUNK_LOADER_REFRESH_TICKS = B
            .comment("A cada quantos ticks o Chunk Loader relê os chunks reivindicados pela colônia (ela cresce).")
            .defineInRange("chunkLoaderRefreshTicks", 1200, 100, 72_000);

    public static final ModConfigSpec.IntValue CHUNK_LOADER_WAKE_GRACE_SECONDS = B
            .comment("Segundos que o loader carrega a área sem energia quando um membro volta, para a rede ME ligar.")
            .defineInRange("chunkLoaderWakeGraceSeconds", 30, 0, 600);

    public static final ModConfigSpec.IntValue MONITOR_MAX_WIDTH = B
            .comment("Largura máxima (em blocos) de uma tela formada por monitores.")
            .defineInRange("monitorMaxWidth", 8, 1, 16);

    public static final ModConfigSpec.IntValue MONITOR_MAX_HEIGHT = B
            .comment("Altura máxima (em blocos) de uma tela formada por monitores.")
            .defineInRange("monitorMaxHeight", 6, 1, 16);

    public static final ModConfigSpec.IntValue MONITOR_LINK_RANGE = B
            .comment("Distância máxima (em blocos) entre a tela de monitor e a ponte ligada pelo cartão.")
            .defineInRange("monitorLinkRange", 64, 8, 256);

    public static final ModConfigSpec.DoubleValue BRIDGE_IDLE_POWER = B
            .comment("Consumo parado da Ponte, em AE/t (vale ao colocar ou recarregar o bloco).")
            .defineInRange("bridgeIdlePower", 4.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue SUPPLY_IDLE_POWER = B
            .comment("Consumo parado do Abastecedor, em AE/t (vale ao colocar ou recarregar o bloco).")
            .defineInRange("supplyIdlePower", 3.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue TERMINAL_IDLE_POWER = B
            .comment("Consumo parado do Terminal do Armazém, em AE/t (vale ao colocar ou recarregar o bloco).")
            .defineInRange("terminalIdlePower", 2.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue TERMINAL_ENERGY_PER_ITEM = B
            .comment("Energia (AE) gasta pelo Terminal do Armazém por item movido entre o armazém e o jogador.")
            .defineInRange("terminalEnergyPerItem", 1.0, 0.0, 1000.0);

    public static final ModConfigSpec.IntValue TERMINAL_SYNC_TICKS = B
            .comment("Intervalo, em ticks, entre leituras do armazém com a tela do Terminal do Armazém aberta.")
            .defineInRange("terminalSyncTicks", 10, 2, 100);

    public static final ModConfigSpec.IntValue TERMINAL_MAX_TYPES = B
            .comment("Máximo de tipos de item mostrados pelo Terminal do Armazém (limita o tráfego em armazéns enormes).")
            .defineInRange("terminalMaxTypes", 4096, 64, 32768);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
