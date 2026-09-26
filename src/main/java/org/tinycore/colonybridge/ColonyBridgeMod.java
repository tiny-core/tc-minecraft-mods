package org.tinycore.colonybridge;

import appeng.api.AECapabilities;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;
import org.tinycore.colonybridge.registry.ModRegistries;

@Mod(ColonyBridgeMod.MOD_ID)
public final class ColonyBridgeMod {
    public static final String MOD_ID = "tccolonybridge";
    public static final Logger LOG = LogUtils.getLogger();

    public ColonyBridgeMod(IEventBus modBus, ModContainer container) {
        ModRegistries.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::addToCreativeTab);
    }

    /** Expõe o nó da grid para que os cabos AE2 se liguem ao bloco. */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModRegistries.COLONY_BRIDGE_BE.get(),
                (be, ctx) -> be);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModRegistries.COLONY_BRIDGE_ITEM.get());
        }
    }
}
