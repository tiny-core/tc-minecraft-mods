package org.tinycore.cloud.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.cloud.TcCloud;

/**
 * Cliente → servidor: um pedido da tela do TC Cloud Link ({@code LinkAction}) e, nas retiradas, a impressão
 * digital do item clicado. Sem quantidade nem posição: o servidor usa a tela que ele sabe estar aberta e decide
 * quanto sai.
 */
public record LinkActionPayload(int containerId, int action, String fingerprint) implements CustomPacketPayload {

    public static final Type<LinkActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TcCloud.MOD_ID, "link_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LinkActionPayload::containerId,
            ByteBufCodecs.VAR_INT, LinkActionPayload::action,
            ByteBufCodecs.stringUtf8(64), LinkActionPayload::fingerprint,
            LinkActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
