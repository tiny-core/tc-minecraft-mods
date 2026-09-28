package org.tinycore.colonybridge.logic.bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Uma linha da lista de pedidos na tela da ponte: ícone, quantidade, descrição e resultado.
 * <p>
 * {@code STREAM_CODEC} descreve como gravar/ler a linha num pacote de rede (≈ um serializador binário
 * em C#). {@code RegistryFriendlyByteBuf} é o buffer que sabe serializar itens com componentes.
 *
 * @param icon    item mostrado (o pedido exato ou um exemplo do que é aceito)
 * @param count   quantidade pedida
 * @param outcome o que a ponte fez com o pedido
 * @param label   descrição curta do MineColonies (ex.: "Qualquer picareta")
 */
public record RequestLine(ItemStack icon, int count, RequestOutcome outcome, Component label) {

    private static final RequestOutcome[] OUTCOMES = RequestOutcome.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLine> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, RequestLine::icon,
            ByteBufCodecs.VAR_INT, RequestLine::count,
            ByteBufCodecs.idMapper(i -> OUTCOMES[Math.floorMod(i, OUTCOMES.length)], RequestOutcome::ordinal),
            RequestLine::outcome,
            ComponentSerialization.STREAM_CODEC, RequestLine::label,
            RequestLine::new);

    /**
     * Igualdade de conteúdo. O {@code equals} do record compararia os ItemStack por referência
     * (ItemStack não sobrescreve equals), então usamos {@code ItemStack.matches}.
     */
    public boolean sameAs(RequestLine other) {
        return count == other.count
                && outcome == other.outcome
                && ItemStack.matches(icon, other.icon)
                && label.equals(other.label);
    }
}
