package org.tinycore.colonybridge.menu.encoder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.logic.encoder.EncoderState;

import java.util.List;

/**
 * Uma linha da tela do TC Pattern Encoder: o item pedido (1 unidade, com componentes), quanto a colônia pede, a
 * situação e os ingredientes da receita escolhida (somados, para o tooltip).
 */
public record EncoderLine(ItemStack result, int amount, EncoderState state, List<ItemStack> inputs) {

    /** Ingredientes: no máximo os 9 da grade. */
    private static final int MAX_INPUTS = 9;

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderLine> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, EncoderLine::result,
            ByteBufCodecs.VAR_INT, EncoderLine::amount,
            ByteBufCodecs.VAR_INT.map(EncoderState::byId, EncoderState::ordinal), EncoderLine::state,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INPUTS)), EncoderLine::inputs,
            EncoderLine::new);

    /** Comparação por conteúdo ({@code ItemStack} não tem {@code equals}): evita reenviar a tela sem mudança. */
    boolean sameAs(EncoderLine other) {
        return amount == other.amount && state == other.state
                && ItemStack.matches(result, other.result) && ItemStack.listMatches(inputs, other.inputs);
    }
}
