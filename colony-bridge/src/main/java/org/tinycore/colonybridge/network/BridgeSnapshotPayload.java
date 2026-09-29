package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.bridge.BridgeSnapshot;

/**
 * Pacote servidor → cliente com o estado da ponte para a tela aberta.
 * {@code containerId} garante que o dado só é aplicado à tela certa (se o jogador trocou de tela
 * no meio do caminho, o pacote é ignorado).
 */
public record BridgeSnapshotPayload(int containerId, BridgeSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<BridgeSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "bridge_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BridgeSnapshotPayload::containerId,
                    BridgeSnapshot.STREAM_CODEC, BridgeSnapshotPayload::snapshot,
                    BridgeSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
