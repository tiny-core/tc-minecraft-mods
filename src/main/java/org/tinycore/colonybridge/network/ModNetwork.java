package org.tinycore.colonybridge.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.client.ClientPayloadHandler;
import org.tinycore.colonybridge.menu.ColonyBridgeMenu;

/**
 * Registro dos pacotes do mod e tratamento dos que chegam ao servidor.
 * <p>
 * Os handlers rodam na thread principal do jogo (padrão do NeoForge 21.1), então podem mexer no
 * mundo com segurança. O handler do cliente só chama {@link ClientPayloadHandler} dentro de uma
 * lambda: a classe de cliente só é carregada quando a lambda executa, o que nunca acontece num
 * servidor dedicado.
 */
public final class ModNetwork {

    /** Versão do protocolo: mudar quando o formato de algum pacote mudar (cliente e servidor precisam casar). */
    private static final String PROTOCOL_VERSION = "4";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(BridgeSnapshotPayload.TYPE, BridgeSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.onSnapshot(payload));
        registrar.playToServer(BridgeSettingsPayload.TYPE, BridgeSettingsPayload.STREAM_CODEC,
                ModNetwork::onSettings);
        registrar.playToServer(FilterSlotPayload.TYPE, FilterSlotPayload.STREAM_CODEC,
                ModNetwork::onFilterSlot);
    }

    private static void onSettings(BridgeSettingsPayload payload, IPayloadContext context) {
        ColonyBridgeMenu menu = validMenu(context, payload.containerId());
        if (menu == null) {
            return;
        }
        menu.getBridge().applySettings(payload.settings());
        menu.requestSync();
    }

    /** Item arrastado do JEI para o filtro. O menu grava só uma cópia de 1 unidade. */
    private static void onFilterSlot(FilterSlotPayload payload, IPayloadContext context) {
        ColonyBridgeMenu menu = validMenu(context, payload.containerId());
        if (menu == null) {
            return;
        }
        menu.setFilterSlot(payload.slot(), payload.stack(), context.player());
    }

    /**
     * Pacote vindo do cliente = hostil até prova em contrário. Devolve o menu só se: a tela da ponte
     * está aberta com esse id, o jogador está a até 8 blocos (stillValid), a ponte ainda existe e o
     * jogador tem permissão para configurá-la. Caso contrário, null (pacote ignorado).
     */
    private static @Nullable ColonyBridgeMenu validMenu(IPayloadContext context, int containerId) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return null;
        }
        if (!(player.containerMenu instanceof ColonyBridgeMenu menu)
                || menu.containerId != containerId
                || !menu.stillValid(player)) {
            return null;
        }
        ColonyBridgeBlockEntity bridge = menu.getBridge();
        if (bridge == null || bridge.isRemoved()) {
            return null;
        }
        if (!bridge.canConfigure(player)) {
            ColonyBridgeMod.LOG.debug("{} tentou configurar a ponte em {} sem permissão",
                    player.getGameProfile().getName(), bridge.getBlockPos());
            return null;
        }
        return menu;
    }
}
