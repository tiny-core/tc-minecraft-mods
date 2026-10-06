package org.tinycore.cloud.menu;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Um canal do jogador na tela do TC Cloud Link (servidor → cliente): id e nome. */
public record LinkChannel(@NotNull UUID id, @NotNull String name) {

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkChannel> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LinkChannel::id,
            ByteBufCodecs.stringUtf8(LinkHeader.MAX_TEXT), LinkChannel::name,
            LinkChannel::new);
}
