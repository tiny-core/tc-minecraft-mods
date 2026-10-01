package org.tinycore.cloud.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

/**
 * Cabeçalho da tela do TC Cloud Link (servidor → cliente): situação da nuvem, configuração do Link e a última
 * recusa de depósito. Textos com teto de tamanho (proteção contra pacote gigante).
 *
 * @param status      {@code CloudStatus.ordinal()}
 * @param detail      complemento da situação (quem está com o canal, motivo do somente leitura)
 * @param access      {@code NetworkAccess.ordinal()}
 * @param conflict    onde o canal já está montado ("" se em lugar nenhum)
 * @param rejection   {@code TransferRejection.ordinal()} da última recusa, ou -1
 * @param networkUp   o nó AE2 está ativo (energia + canal)
 */
public record LinkHeader(int status, @NotNull String detail, int access, int priority, @NotNull String conflict,
                         int rejection, boolean networkUp) {

    static final int MAX_TEXT = 96;

    public static final LinkHeader EMPTY = new LinkHeader(0, "", 2, 0, "", -1, false);

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkHeader> STREAM_CODEC = StreamCodec.of(
            (buf, h) -> {
                buf.writeVarInt(h.status);
                buf.writeUtf(h.detail, MAX_TEXT);
                buf.writeVarInt(h.access);
                buf.writeVarInt(h.priority);
                buf.writeUtf(h.conflict, MAX_TEXT);
                buf.writeVarInt(h.rejection + 1);
                buf.writeBoolean(h.networkUp);
            },
            buf -> new LinkHeader(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt() - 1, buf.readBoolean()));

    /** Corta textos no teto do pacote. */
    static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
