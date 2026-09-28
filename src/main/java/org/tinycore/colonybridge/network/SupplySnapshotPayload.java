package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.SupplySnapshot;

/**
 * Pacote servidor → cliente com o estado do bloco de abastecimento para a tela aberta.
 * {@code containerId} garante que o dado só é aplicado à tela certa.
 */
public record SupplySnapshotPayload(int containerId, SupplySnapshot snapshot) implements CustomPacketPayload {

    public static final Type<SupplySnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "supply_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplySnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SupplySnapshotPayload::containerId,
                    SupplySnapshot.STREAM_CODEC, SupplySnapshotPayload::snapshot,
                    SupplySnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
