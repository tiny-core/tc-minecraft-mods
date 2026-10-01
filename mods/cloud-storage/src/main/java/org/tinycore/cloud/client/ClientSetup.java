package org.tinycore.cloud.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.registry.ModMenus;

/**
 * Registros que só existem no cliente. {@code @EventBusSubscriber(value = Dist.CLIENT)} faz o NeoForge carregar
 * esta classe só no cliente, então o servidor dedicado nunca toca em classes de tela.
 */
@EventBusSubscriber(modid = TcCloud.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CLOUD_LINK.get(), CloudLinkScreen::new);
    }
}
