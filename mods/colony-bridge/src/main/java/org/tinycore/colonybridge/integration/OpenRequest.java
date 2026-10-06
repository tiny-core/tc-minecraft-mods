package org.tinycore.colonybridge.integration;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Pedido em aberto que o armazém não conseguiu resolver.
 *
 * @param token       token do pedido no request manager
 * @param deliverable o que o cidadão aceita (item exato, tag, ferramenta, comida...)
 * @param exactStack  item exato quando o pedido é um Stack; vazio caso contrário
 *                    (só pedidos exatos são candidatos a autocrafting no MVP)
 * @param icon        item para mostrar na tela (o exato ou um exemplo do que é aceito); pode ser vazio
 * @param label       descrição curta do pedido, vinda do MineColonies
 * @param examples    alguns itens que o pedido aceita (os que o MineColonies mostra; no máximo
 *                    {@code ColonyAccess.MAX_EXAMPLES}), para o Pattern Encoder em pedidos por tag
 */
public record OpenRequest(IToken<?> token, IDeliverable deliverable, ItemStack exactStack,
                          ItemStack icon, Component label, List<ItemStack> examples) {

    /**
     * Id estável do pedido, igual entre reinícios (o token do MineColonies é um UUID salvo com a colônia).
     * Usado como chave no {@code DeliveryLedger}.
     */
    public String id() {
        return String.valueOf(token.getIdentifier());
    }

    public int amount() {
        return Math.max(1, deliverable.getCount());
    }

    public boolean isExact() {
        return !exactStack.isEmpty();
    }
}
