package org.tinycore.colonybridge.integration;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Pedido em aberto que o armazém não conseguiu resolver, já traduzido para tipos do Minecraft.
 * <p>
 * Os objetos do MineColonies (token e "deliverable") ficam <b>privados</b>: só o pacote {@code integration} os
 * usa ({@link ColonyAccess#reassign}). O resto do mod pergunta "o pedido aceita este item?" por {@link #accepts}
 * e nunca importa a API do MineColonies; assim uma mudança nela quebra um pacote só.
 */
public final class OpenRequest {

    private final IToken<?> token;
    private final IDeliverable deliverable;
    private final ItemStack exactStack;
    private final ItemStack icon;
    private final Component label;
    private final List<ItemStack> examples;

    /**
     * @param exactStack item exato quando o pedido é um Stack; vazio caso contrário
     * @param icon       item para mostrar na tela (o exato ou um exemplo do que é aceito); pode ser vazio
     * @param label      descrição curta do pedido, vinda do MineColonies
     * @param examples   alguns itens que o pedido aceita (no máximo {@code ColonyAccess.MAX_EXAMPLES}), para o
     *                   Pattern Encoder em pedidos por tag
     */
    OpenRequest(IToken<?> token, IDeliverable deliverable, ItemStack exactStack, ItemStack icon, Component label,
                List<ItemStack> examples) {
        this.token = token;
        this.deliverable = deliverable;
        this.exactStack = exactStack;
        this.icon = icon;
        this.label = label;
        this.examples = examples;
    }

    /** Token do request manager (só para o MineColonies, dentro deste pacote). */
    IToken<?> token() {
        return token;
    }

    /**
     * Id estável do pedido, igual entre reinícios (o token do MineColonies é um UUID salvo com a colônia).
     * Usado como chave no {@code DeliveryLedger}.
     */
    public String id() {
        return String.valueOf(token.getIdentifier());
    }

    /** true se o cidadão aceita este item (item exato, tag, ferramenta, comida...). */
    public boolean accepts(ItemStack stack) {
        return deliverable.matches(stack);
    }

    public int amount() {
        return Math.max(1, deliverable.getCount());
    }

    public boolean isExact() {
        return !exactStack.isEmpty();
    }

    public ItemStack exactStack() {
        return exactStack;
    }

    public ItemStack icon() {
        return icon;
    }

    public Component label() {
        return label;
    }

    public List<ItemStack> examples() {
        return examples;
    }
}
