package org.tinycore.colonybridge.integration;

import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.world.item.ItemStack;

/**
 * Pedido em aberto que o armazém não conseguiu resolver.
 *
 * @param token       token do pedido no request manager
 * @param deliverable o que o cidadão aceita (item exato, tag, ferramenta, comida...)
 * @param exactStack  item exato quando o pedido é um Stack; vazio caso contrário
 *                    (só pedidos exatos são candidatos a autocrafting no MVP)
 */
public record OpenRequest(IToken<?> token, IDeliverable deliverable, ItemStack exactStack) {

    public int amount() {
        return Math.max(1, deliverable.getCount());
    }

    public boolean isExact() {
        return !exactStack.isEmpty();
    }
}
