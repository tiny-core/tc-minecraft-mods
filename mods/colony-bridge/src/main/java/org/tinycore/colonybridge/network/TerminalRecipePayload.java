package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.ColonyBridgeMod;

import java.util.List;

/**
 * Pacote cliente → servidor: botão "+" do JEI na bancada do Terminal do Armazém. Para cada uma das 9
 * posições da grade, a lista de itens que servem (vazia = posição sem ingrediente); {@code max} = Shift
 * (um stack por posição).
 * <p>
 * O codec limita as listas (9 posições, {@link #MAX_OPTIONS} opções cada) para um cliente malicioso não
 * mandar um pacote gigante. As opções são só filtros: o servidor pega itens que já existem no armazém
 * ou no inventário.
 */
public record TerminalRecipePayload(int containerId, List<List<ItemStack>> slots, boolean max)
        implements CustomPacketPayload {

    public static final int MAX_OPTIONS = 64;

    public static final Type<TerminalRecipePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "terminal_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRecipePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TerminalRecipePayload::containerId,
                    ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_OPTIONS)).apply(ByteBufCodecs.list(9)),
                    TerminalRecipePayload::slots,
                    ByteBufCodecs.BOOL, TerminalRecipePayload::max,
                    TerminalRecipePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
