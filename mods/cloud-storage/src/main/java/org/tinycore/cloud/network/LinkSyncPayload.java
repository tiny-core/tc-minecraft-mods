package org.tinycore.cloud.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.menu.LinkEntry;
import org.tinycore.cloud.menu.LinkHeader;

import java.util.List;

/**
 * Servidor → cliente: o que mudou na tela do TC Cloud Link aberta. {@code reset} = esquecer tudo antes (primeiro
 * envio). A lista vai em pedaços de até {@link #MAX_ENTRIES}; o codec recusa listas maiores.
 */
public record LinkSyncPayload(int containerId, boolean reset, LinkHeader header, List<LinkEntry> entries)
        implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 256;

    public static final Type<LinkSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TcCloud.MOD_ID, "link_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LinkSyncPayload::containerId,
            ByteBufCodecs.BOOL, LinkSyncPayload::reset,
            LinkHeader.STREAM_CODEC, LinkSyncPayload::header,
            LinkEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), LinkSyncPayload::entries,
            LinkSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
