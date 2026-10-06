package org.tinycore.cloud.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.client.ClientPayloadHandler;
import org.tinycore.cloud.menu.CloudLinkMenu;
import org.tinycore.cloud.menu.LinkAction;

/**
 * Registro dos pacotes do mod e tratamento dos que chegam ao servidor. Os handlers rodam na thread principal
 * (padrão do NeoForge 21.1). O handler do cliente só chama {@link ClientPayloadHandler} dentro de uma lambda: a
 * classe de cliente só é carregada quando a lambda roda, o que nunca acontece num servidor dedicado.
 */
public final class ModNetwork {

    /** Mudar quando o formato de algum pacote mudar (cliente e servidor precisam casar). */
    private static final String PROTOCOL_VERSION = "2";

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(LinkSyncPayload.TYPE, LinkSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.onLinkSync(payload));
        registrar.playToServer(LinkActionPayload.TYPE, LinkActionPayload.STREAM_CODEC, ModNetwork::onAction);
    }

    /**
     * Pacote hostil até prova em contrário: só vale para a tela do Link que o servidor sabe estar aberta, ainda
     * válida (dono, distância ≤ 8 blocos, bloco existe) e com uma ação conhecida.
     */
    private static void onAction(LinkActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof CloudLinkMenu menu) || menu.containerId != payload.containerId()
                || !menu.stillValid(player)) {
            TcCloud.LOG.debug("Pacote de ação do Cloud Link inválido de {}", player.getGameProfile().getName());
            return;
        }
        LinkAction action = LinkAction.byId(payload.action());
        if (action == null) return;
        menu.handleAction(player, action, payload.fingerprint());
    }
}
