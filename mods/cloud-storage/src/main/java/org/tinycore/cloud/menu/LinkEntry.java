package org.tinycore.cloud.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Um item do canal como a tela o recebe. {@code icon} vazio = o item não existe neste servidor (a grade mostra o
 * ícone substituto e o nome guardado). {@code amount == 0} numa atualização = o item saiu do canal.
 *
 * @param status {@code ItemCompatibility.ordinal()}
 */
public record LinkEntry(@NotNull String fingerprint, @NotNull ItemStack icon, @NotNull String itemId,
                        @NotNull String name, long amount, int status) {

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), LinkEntry::fingerprint,
            ItemStack.OPTIONAL_STREAM_CODEC, LinkEntry::icon,
            ByteBufCodecs.stringUtf8(LinkHeader.MAX_TEXT), LinkEntry::itemId,
            ByteBufCodecs.stringUtf8(LinkHeader.MAX_TEXT), LinkEntry::name,
            ByteBufCodecs.VAR_LONG, LinkEntry::amount,
            ByteBufCodecs.VAR_INT, LinkEntry::status,
            LinkEntry::new);
}
