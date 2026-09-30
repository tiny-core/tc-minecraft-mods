package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderSnapshot;

/** Servidor → cliente: situação do Chunk Loader para a tela aberta ({@code containerId} = tela certa). */
public record ChunkLoaderSnapshotPayload(int containerId, ChunkLoaderSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<ChunkLoaderSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "chunk_loader_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkLoaderSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ChunkLoaderSnapshotPayload::containerId,
                    ChunkLoaderSnapshot.STREAM_CODEC, ChunkLoaderSnapshotPayload::snapshot,
                    ChunkLoaderSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
