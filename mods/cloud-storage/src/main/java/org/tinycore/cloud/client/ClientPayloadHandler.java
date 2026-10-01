package org.tinycore.cloud.client;

import net.minecraft.client.Minecraft;
import org.tinycore.cloud.menu.CloudLinkMenu;
import org.tinycore.cloud.network.LinkSyncPayload;

/** Pacotes que chegam ao cliente. Só é carregada no cliente (ver {@code ModNetwork}). */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    public static void onLinkSync(LinkSyncPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof CloudLinkMenu menu
                && menu.containerId == payload.containerId()) {
            menu.view().apply(payload.reset(), payload.header(), payload.entries());
        }
    }
}
