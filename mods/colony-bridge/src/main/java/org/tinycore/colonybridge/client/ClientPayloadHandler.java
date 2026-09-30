package org.tinycore.colonybridge.client;

import net.minecraft.client.Minecraft;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderMenu;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.network.ChunkLoaderSnapshotPayload;
import org.tinycore.colonybridge.network.SupplySnapshotPayload;
import org.tinycore.colonybridge.network.TabletPanelPayload;
import org.tinycore.colonybridge.network.TargetListPayload;
import org.tinycore.colonybridge.menu.tablet.TabletPanelMenu;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.TargetListMenu;
import org.tinycore.colonybridge.network.WarehouseContentsPayload;

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

    /** Conteúdo do armazém para a tela do Terminal do Armazém, se ela ainda é a tela aberta. */
    public static void onWarehouseContents(WarehouseContentsPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null
                && player.containerMenu instanceof WarehouseTerminalMenu menu
                && menu.containerId == payload.containerId()) {
            menu.getView().apply(payload.reset(), BridgeStatus.values()[Math.floorMod(payload.status(), BridgeStatus.values().length)],
                    payload.entries());
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

    /** Linhas de uma lista (Abastecedor ou filtro da Ponte) para a tela aberta, se ela ainda é a mesma. */
    public static void onTargetList(TargetListPayload payload) {
        var player = Minecraft.getInstance().player;
        TargetListKind[] kinds = TargetListKind.values();
        if (player != null
                && player.containerMenu instanceof TargetListMenu menu
                && player.containerMenu.containerId == payload.containerId()
                && payload.kind() >= 0 && payload.kind() < kinds.length) {
            menu.targetLists().accept(kinds[payload.kind()], payload.lines());
        }
    }

    /** Dados do painel para a aba de painel do tablet, se ela ainda é a tela aberta. */
    public static void onTabletPanel(TabletPanelPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null
                && player.containerMenu instanceof TabletPanelMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setData(MonitorData.load(payload.data(), player.registryAccess()));
        }
    }

    /** Situação do Chunk Loader para a tela aberta, se ela ainda é a mesma. */
    public static void onChunkLoaderSnapshot(ChunkLoaderSnapshotPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null
                && player.containerMenu instanceof ChunkLoaderMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setSnapshot(payload.snapshot());
        }
    }
}
