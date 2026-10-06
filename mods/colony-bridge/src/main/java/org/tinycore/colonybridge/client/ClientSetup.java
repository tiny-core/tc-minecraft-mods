package org.tinycore.colonybridge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.client.bridge.ColonyBridgeScreen;
import org.tinycore.colonybridge.client.render.MonitorRenderer;
import org.tinycore.colonybridge.client.supply.ColonySupplyScreen;
import org.tinycore.colonybridge.client.loader.ChunkLoaderScreen;
import org.tinycore.colonybridge.client.encoder.PatternEncoderScreen;
import org.tinycore.colonybridge.client.tablet.TabletPanelScreen;
import org.tinycore.colonybridge.client.terminal.WarehouseTerminalScreen;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * Registros que só existem no cliente. {@code @EventBusSubscriber(value = Dist.CLIENT)} faz o NeoForge
 * carregar esta classe apenas no cliente, então o servidor dedicado nunca toca em classes de tela.
 * O NeoForge escolhe sozinho o barramento certo pelo tipo do evento (aqui, um evento de inicialização do mod).
 */
@EventBusSubscriber(modid = ColonyBridgeMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    /** Liga o tipo de menu da ponte à sua tela. */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COLONY_BRIDGE.get(), ColonyBridgeScreen::new);
        event.register(ModMenus.COLONY_SUPPLY.get(), ColonySupplyScreen::new);
        event.register(ModMenus.WAREHOUSE_TERMINAL.get(), WarehouseTerminalScreen::new);
        event.register(ModMenus.TABLET_PANEL.get(), TabletPanelScreen::new);
        event.register(ModMenus.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
        event.register(ModMenus.PATTERN_ENCODER.get(), PatternEncoderScreen::new);
    }

    /** Liga o block entity do monitor ao renderer que desenha a tela no mundo. */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.COLONY_MONITOR.get(), MonitorRenderer::new);
    }
}
