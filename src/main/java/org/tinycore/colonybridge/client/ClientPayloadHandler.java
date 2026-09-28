package org.tinycore.colonybridge.client;

import net.minecraft.client.Minecraft;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.network.SupplySnapshotPayload;

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

    /** Idem para a tela do bloco de abastecimento. */
    public static void onSupplySnapshot(SupplySnapshotPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null
                && player.containerMenu instanceof ColonySupplyMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setSnapshot(payload.snapshot());
        }
    }
}
