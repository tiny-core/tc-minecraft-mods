package org.tinycore.cloud.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
 * @param quota       uso da cota do canal ({@link LinkQuota#NONE} sem sessão)
 * @param channels    canais do jogador (no máximo {@link #MAX_CHANNELS})
 * @param selected    índice do canal do Link em {@code channels} (-1 sem canais)
 * @param feedback    {@code ChannelFeedback.ordinal()} do último criar/renomear (0 = nada)
 */
public record LinkHeader(int status, @NotNull String detail, int access, int priority, @NotNull String conflict,
                         int rejection, boolean networkUp, @NotNull LinkQuota quota,
                         @NotNull List<LinkChannel> channels, int selected, int feedback) {

    static final int MAX_TEXT = 96;
    /** Teto de canais no pacote (a config limita a 64). */
    public static final int MAX_CHANNELS = 64;

    public static final LinkHeader EMPTY = new LinkHeader(0, "", 2, 0, "", -1, false, LinkQuota.NONE,
            List.of(), -1, 0);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<LinkChannel>> CHANNELS_CODEC =
            LinkChannel.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHANNELS));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkHeader> STREAM_CODEC = StreamCodec.of(
            (buf, h) -> {
                buf.writeVarInt(h.status);
                buf.writeUtf(h.detail, MAX_TEXT);
                buf.writeVarInt(h.access);
                buf.writeVarInt(h.priority);
                buf.writeUtf(h.conflict, MAX_TEXT);
                buf.writeVarInt(h.rejection + 1);
                buf.writeBoolean(h.networkUp);
                LinkQuota.STREAM_CODEC.encode(buf, h.quota);
                CHANNELS_CODEC.encode(buf, h.channels);
                buf.writeVarInt(h.selected + 1);
                buf.writeVarInt(h.feedback);
            },
            buf -> new LinkHeader(buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf(MAX_TEXT), buf.readVarInt() - 1, buf.readBoolean(), LinkQuota.STREAM_CODEC.decode(buf),
                    CHANNELS_CODEC.decode(buf), buf.readVarInt() - 1, buf.readVarInt()));

    /** Corta textos no teto do pacote. */
    static String clip(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
