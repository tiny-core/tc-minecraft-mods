package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Cliente → servidor: "abra esta aba do tablet". Só leva o número da aba; o servidor refaz tudo
 * ({@code TabletOpener}): tablet na mão, ligação, bateria, bloco disponível e permissão.
 *
 * @param tab ordinal do {@code TabletTab}
 */
public record TabletOpenPayload(int tab) implements CustomPacketPayload {

    public static final Type<TabletOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "tablet_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TabletOpenPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TabletOpenPayload::tab, TabletOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
