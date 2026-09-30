package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Cliente → servidor: botão da tela do Chunk Loader. Só o número da ação; o menu é validado no servidor
 * ({@code ModNetwork.validMenu}: tela aberta, acesso válido, permissão).
 *
 * @param action {@link #TOGGLE} ou {@link #REDSTONE}
 */
public record ChunkLoaderActionPayload(int containerId, int action) implements CustomPacketPayload {

    public static final int TOGGLE = 0;
    public static final int REDSTONE = 1;

    public static final Type<ChunkLoaderActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "chunk_loader_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkLoaderActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ChunkLoaderActionPayload::containerId,
                    ByteBufCodecs.VAR_INT, ChunkLoaderActionPayload::action,
                    ChunkLoaderActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
