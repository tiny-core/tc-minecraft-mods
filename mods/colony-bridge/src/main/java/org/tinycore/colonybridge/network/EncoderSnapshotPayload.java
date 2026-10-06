package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.encoder.EncoderSnapshot;

/** Servidor → cliente: lista do TC Pattern Encoder para a tela aberta ({@code containerId} = tela certa). */
public record EncoderSnapshotPayload(int containerId, EncoderSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<EncoderSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "encoder_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EncoderSnapshotPayload::containerId,
                    EncoderSnapshot.STREAM_CODEC, EncoderSnapshotPayload::snapshot,
                    EncoderSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
