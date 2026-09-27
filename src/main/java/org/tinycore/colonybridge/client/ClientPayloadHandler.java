package org.tinycore.colonybridge.client;

import net.minecraft.client.Minecraft;
import org.tinycore.colonybridge.menu.ColonyBridgeMenu;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;

/**
 * Trata, no cliente, os pacotes vindos do servidor. Fica em {@code client/} porque usa
 * {@link Minecraft}, que não existe num servidor dedicado.
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {}

    /** Entrega o snapshot à tela da ponte, se ela ainda é a tela aberta. */
    public static void onSnapshot(BridgeSnapshotPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null
                && player.containerMenu instanceof ColonyBridgeMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setSnapshot(payload.snapshot());
        }
    }
}
