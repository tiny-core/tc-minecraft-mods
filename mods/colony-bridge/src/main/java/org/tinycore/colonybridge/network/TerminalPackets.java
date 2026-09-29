package org.tinycore.colonybridge.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.logic.terminal.TerminalAction;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;

import java.util.List;

/**
 * Tratamento no servidor dos pacotes do Terminal do Armazém. Separado do {@link ModNetwork} porque o
 * terminal não tem block entity: a validação é outra (permissão na colônia, não dono do bloco).
 */
final class TerminalPackets {

    private TerminalPackets() {}

    /**
     * Clique na grade. Pacote hostil até prova em contrário: só vale se o menu aberto é um terminal com
     * esse id, o jogador está a até 8 blocos ({@code stillValid}), a ação existe e o jogador ainda tem
     * permissão na colônia ({@code racks()} devolve null se não tiver).
     */
    static void onAction(WarehouseActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof WarehouseTerminalMenu menu)
                || menu.containerId != payload.containerId()
                || !menu.stillValid(player)) {
            return;
        }
        TerminalAction action = TerminalAction.byId(payload.action());
        List<IItemHandler> racks = menu.racks();
        if (action == null || racks == null) {
            ColonyBridgeMod.LOG.debug("Ação do terminal recusada para {} (ação {}, sem permissão ou colônia?)",
                    player.getGameProfile().getName(), payload.action());
            return;
        }
        menu.handleAction(player, racks, action, payload.item());
    }
}
