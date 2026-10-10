package org.tinycore.cloud;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.tinycore.cloud.integration.Ae2Compat;
import org.tinycore.cloud.network.ModNetwork;
import org.tinycore.cloud.registry.ModBlockEntities;
import org.tinycore.cloud.registry.ModBlocks;
import org.tinycore.cloud.registry.ModCreativeTabs;
import org.tinycore.cloud.registry.ModItems;
import org.tinycore.cloud.registry.ModMenus;
import org.tinycore.cloud.server.CloudServerEvents;

/**
 * Entrada do <b>TC Cloud Storage</b>: armazenamento de itens fora do mundo, no banco do TCMine, que a rede
 * do AE2 enxerga como um disco. Plano completo em {@code docs/planos/tc-cloud-storage.md} (raiz do workspace).
 *
 * <p>Pacotes:
 * <ul>
 *   <li>{@code cloud}: saldos do jogador, "débito cedo, crédito tarde" e lotes numerados;</li>
 *   <li>{@code cloud.journal}: diário em disco (lotes, confirmações, marcas de save) e checkpoint;</li>
 *   <li>{@code item}: impressão digital canônica do item e detecção de referência ao mundo;</li>
 *   <li>{@code item.policy}: regras de item do dono da nuvem;</li>
 *   <li>{@code server}: o serviço da nuvem no servidor (sessões, diário, envio, eventos);</li>
 *   <li>{@code integration.tcmine}: backends (nuvem local num arquivo; TCMine);</li>
 *   <li>{@code integration.ae2}: o que fala com o AE2 (nó, armazenamento, células), <b>opcional</b>: só é usado com
 *       o AE2 instalado ({@code integration.Ae2Compat});</li>
 *   <li>{@code block}, {@code menu}, {@code network}, {@code client}, {@code registry}: o TC Cloud Link e sua tela.</li>
 * </ul>
 * {@code @Mod} (≈ atributo em C#) diz ao NeoForge que esta classe é a entrada do mod {@value #MOD_ID}.
 */
@Mod(TcCloud.MOD_ID)
public final class TcCloud {

    public static final String MOD_ID = "tccloud";
    public static final Logger LOG = LogUtils.getLogger();

    public TcCloud(IEventBus modBus, ModContainer container) {
        // COMMON, não SERVER: a config SERVER é sincronizada para os clientes (ver Config).
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModNetwork::register);
        Ae2Compat.init(modBus); // AE2 opcional: só registra a parte dele se estiver instalado
        CloudServerEvents.register(NeoForge.EVENT_BUS);
    }
}
