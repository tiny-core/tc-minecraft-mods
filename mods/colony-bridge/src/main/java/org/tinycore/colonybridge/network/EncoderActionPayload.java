package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Cliente → servidor: "codificar" no TC Pattern Encoder. Leva só o item da linha clicada (vazio = todas as linhas
 * prontas); a receita, os ingredientes e o gasto de Blank Pattern são decididos pelo servidor.
 */
public record EncoderActionPayload(int containerId, ItemStack item) implements CustomPacketPayload {

    public static final Type<EncoderActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "encoder_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EncoderActionPayload::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC, EncoderActionPayload::item,
                    EncoderActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
