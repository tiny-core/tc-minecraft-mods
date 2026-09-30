package org.tinycore.colonybridge.client.list;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.network.TargetEditPayload;
import org.tinycore.colonybridge.network.TargetEditPayload.Op;

/**
 * Monta e envia os {@link TargetEditPayload} de uma lista da tela aberta (cliente → servidor). Só envia: a
 * lista na tela muda quando o servidor responde com a lista nova.
 */
final class TargetEditSender {

    private final int containerId;
    private final TargetListKind kind;

    TargetEditSender(int containerId, TargetListKind kind) {
        this.containerId = containerId;
        this.kind = kind;
    }

    void send(Op op, int index) {
        sendText(op, index, "");
    }

    void sendText(Op op, int index, String text) {
        PacketDistributor.sendToServer(TargetEditPayload.of(containerId, kind.ordinal(), op, index, text, 0, false));
    }

    void sendAmount(int index, int amount, boolean all) {
        PacketDistributor.sendToServer(
                TargetEditPayload.of(containerId, kind.ordinal(), Op.SET_AMOUNT, index, "", amount, all));
    }

    /** Item do JEI (ou o item original, ao voltar de uma tag): só um modelo de 1 unidade. */
    void sendStack(Op op, int index, ItemStack stack) {
        PacketDistributor.sendToServer(new TargetEditPayload(containerId, kind.ordinal(), op.ordinal(), index, "", 0,
                false, stack.copyWithCount(1)));
    }
}
