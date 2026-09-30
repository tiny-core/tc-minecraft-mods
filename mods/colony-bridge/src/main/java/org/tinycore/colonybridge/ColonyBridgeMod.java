package org.tinycore.colonybridge;

import appeng.api.AECapabilities;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import org.tinycore.colonybridge.network.ModNetwork;
import org.tinycore.colonybridge.registry.ModCreativeTabs;
import org.tinycore.colonybridge.registry.ModMenus;
import org.tinycore.colonybridge.item.TabletEnergy;
import org.tinycore.colonybridge.logic.loader.ChunkTickets;
import org.tinycore.colonybridge.logic.loader.LoaderEvents;
import org.tinycore.colonybridge.registry.ModBlocks;
import org.tinycore.colonybridge.registry.ModDataComponents;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Ponto de entrada do mod: o NeoForge instancia esta classe (anotação {@code @Mod}) ao carregar.
 * Registra blocos/itens/abas/menus, pacotes de rede, a config de servidor e expõe o nó AE2 da ponte como capability.
 */
@Mod(ColonyBridgeMod.MOD_ID)
public final class ColonyBridgeMod {
    public static final String MOD_ID = "tccolonybridge";
    public static final Logger LOG = LogUtils.getLogger();

    public ColonyBridgeMod(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModDataComponents.register(modBus);
        ModBlockEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        ModMenus.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modBus.addListener(this::registerCapabilities);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(ChunkTickets::register);
        // Eventos do jogo (não do mod): jogador entrou/saiu acorda os Chunk Loaders.
        NeoForge.EVENT_BUS.addListener(LoaderEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(LoaderEvents::onLogout);
    }

    /**
     * Expõe o nó da grid para que os cabos AE2 se liguem aos blocos. O AE2 acha vizinhos só por esta
     * capability ({@code GridHelper.getNodeHost}), então todo bloco com nó ME precisa estar aqui.
     */
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.COLONY_BRIDGE.get(),
                (be, ctx) -> be);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.COLONY_SUPPLY.get(),
                (be, ctx) -> be);
        // O Terminal e o Chunk Loader também têm nó ME: sem esta capability o cabo não se liga a eles.
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.WAREHOUSE_TERMINAL.get(),
                (be, ctx) -> be);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.CHUNK_LOADER.get(),
                (be, ctx) -> be);
        // Bateria do tablet como energia padrão do NeoForge: carregadores de outros mods também a enchem.
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> TabletEnergy.storage(stack),
                ModItems.COLONY_TABLET.get());
    }
}
