package org.tinycore.colonybridge.logic.bridge;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.integration.OpenRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Procura na rede ME os itens que atendem um pedido. Um pedido por tag pode ser atendido por
 * <b>vários</b> itens ao mesmo tempo (ex.: 10 tábuas de carvalho + 10 de bétula para um pedido de 32
 * tábuas), então a busca devolve uma lista, e não um item só.
 * <p>
 * Ordem: o item exato do pedido primeiro (se houver), depois os outros compatíveis na ordem da rede.
 * A busca para quando junta o suficiente, para não varrer redes enormes à toa.
 * Usada pelo {@link BridgeLogic}; o estoque vem do {@link BridgeCycle} (já descontado o que saiu no ciclo).
 */
final class StockSearch {

    private StockSearch() {}

    /**
     * Resultado da busca.
     *
     * @param keys     itens da rede que servem e que o filtro permite, na ordem de entrega
     * @param total    quanto existe somando todos eles (pode passar do pedido no último item)
     * @param filtered havia item compatível, mas o filtro da ponte barrou
     */
    record Result(List<AEItemKey> keys, long total, boolean filtered) {
        boolean isEmpty() {
            return keys.isEmpty();
        }
    }

    /**
     * @param allowed filtro da ponte
     * @param wanted  quanto o pedido ainda precisa; a busca para ao alcançar esse valor
     */
    static Result find(BridgeCycle c, OpenRequest request, Predicate<ItemStack> allowed, long wanted) {
        List<AEItemKey> keys = new ArrayList<>(2);
        long total = 0;
        boolean filtered = false;

        AEItemKey exact = request.isExact() ? AEItemKey.of(request.exactStack()) : null;
        if (exact != null && c.available(exact) > 0) {
            if (allowed.test(exact.getReadOnlyStack())) {
                keys.add(exact);
                total += c.available(exact);
            } else {
                filtered = true;
            }
        }
        // Pedidos por tag / ferramenta / comida (ou variantes do item exato): testa cada item da rede.
        // getReadOnlyStack() devolve um ItemStack que a própria chave guarda em cache: zero alocação
        // por item, o que importa em redes grandes do ATM10. Não pode ser modificado — matches() só lê.
        for (Object2LongMap.Entry<AEKey> entry : c.stock()) {
            if (total >= wanted) {
                break;
            }
            if (entry.getLongValue() <= 0
                    || !(entry.getKey() instanceof AEItemKey itemKey)
                    || itemKey.equals(exact)
                    || c.available(itemKey) <= 0
                    || !request.deliverable().matches(itemKey.getReadOnlyStack())) {
                continue;
            }
            if (allowed.test(itemKey.getReadOnlyStack())) {
                keys.add(itemKey);
                total += c.available(itemKey);
            } else {
                filtered = true; // continua procurando outro item compatível
            }
        }
        return new Result(keys, total, filtered);
    }
}
