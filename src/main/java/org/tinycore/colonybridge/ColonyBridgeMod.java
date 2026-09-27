package org.tinycore.colonybridge;

import appeng.api.AECapabilities;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.tinycore.colonybridge.network.ModNetwork;
import org.tinycore.colonybridge.registry.ModCreativeTabs;
import org.tinycore.colonybridge.registry.ModMenus;
import org.tinycore.colonybridge.registry.ModRegistries;

/**
 * Ponto de entrada do mod: o NeoForge instancia esta classe (anotação {@code @Mod}) ao carregar.
 * Registra blocos/itens/abas/menus, pacotes de rede, a config de servidor e expõe o nó AE2 da ponte como capability.
 */
@Mod(ColonyBridgeMod.MOD_ID)
public final class ColonyBridgeMod {
    public static final String MOD_ID = "tccolonybridge";
    public static final Logger LOG = LogUtils.getLogger();

    public ColonyBridgeMod(IEventBus modBus, ModContainer container) {
        ModRegistries.register(modBus);
        ModCreativeTabs.register(modBus);
        ModMenus.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(ModNetwork::register);
    }

    /** Expõe o nó da grid para que os cabos AE2 se liguem ao bloco. */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModRegistries.COLONY_BRIDGE_BE.get(),
                (be, ctx) -> be);
    }
}
