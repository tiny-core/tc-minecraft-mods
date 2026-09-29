package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Pacote cliente → servidor: "coloque este item no ghost slot N" (filtro ou itens preferidos da ponte). Usado quando o jogador arrasta
 * um item do JEI (que não está no inventário, então não passa pelo clique normal de slot).
 * <p>
 * O item é só um modelo para o filtro: o servidor guarda uma cópia com quantidade 1 e nunca o
 * entrega a ninguém, então um cliente mentindo o item não ganha nada. Validação em {@link ModNetwork}.
 */
public record FilterSlotPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {

    public static final Type<FilterSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "filter_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FilterSlotPayload::containerId,
                    ByteBufCodecs.VAR_INT, FilterSlotPayload::slot,
                    ItemStack.OPTIONAL_STREAM_CODEC, FilterSlotPayload::stack,
                    FilterSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
