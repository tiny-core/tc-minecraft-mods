package org.tinycore.colonybridge.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.BridgeSettings;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.block.RedstoneMode;
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
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(BridgeSnapshotPayload.TYPE, BridgeSnapshotPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.onSnapshot(payload));
        registrar.playToServer(BridgeSettingsPayload.TYPE, BridgeSettingsPayload.STREAM_CODEC,
                ModNetwork::onSettings);
    }

    /**
     * Pacote vindo do cliente = hostil até prova em contrário. Só aplica se:
     * a tela da ponte está aberta com esse id, o jogador está a até 8 blocos (stillValid),
     * tem permissão para configurar e o valor do modo de redstone é válido.
     */
    private static void onSettings(BridgeSettingsPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof ColonyBridgeMenu menu)
                || menu.containerId != payload.containerId()
                || !menu.stillValid(player)) {
            return;
        }
        ColonyBridgeBlockEntity bridge = menu.getBridge();
        if (bridge == null || bridge.isRemoved()) {
            return;
        }
        if (payload.redstoneMode() < 0 || payload.redstoneMode() >= RedstoneMode.values().length) {
            ColonyBridgeMod.LOG.warn("Modo de redstone inválido ({}) enviado por {}",
                    payload.redstoneMode(), player.getGameProfile().getName());
            return;
        }
        if (!bridge.canConfigure(player)) {
            ColonyBridgeMod.LOG.debug("{} tentou configurar a ponte em {} sem permissão",
                    player.getGameProfile().getName(), bridge.getBlockPos());
            return;
        }
        bridge.applySettings(new BridgeSettings(payload.craftingEnabled(),
                RedstoneMode.byId(payload.redstoneMode())));
        menu.requestSync();
    }
}
