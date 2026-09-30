package org.tinycore.colonybridge.logic.target;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "Este item passa nesta linha?" — a comparação usada pelo filtro da Ponte e pelo Abastecedor.
 * <ul>
 *   <li><b>item</b>: {@code exact} = mesmo item e mesmos componentes (encantamentos, durabilidade...);
 *       senão, só o tipo do item. Linha de item inválida (mod removido) não aceita nada;</li>
 *   <li><b>tag</b>: o item está na tag (consulta direta ao item, sem montar listas);</li>
 *   <li><b>mod</b>: o item é daquele mod.</li>
 * </ul>
 */
public final class TargetMatcher {

    private TargetMatcher() {}

    public static boolean matches(TargetLine line, ItemStack stack, boolean exact) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (line.spec().kind()) {
            case ITEM -> !line.item().isEmpty() && (exact
                    ? ItemStack.isSameItemSameComponents(line.item(), stack)
                    : stack.is(line.item().getItem()));
            case TAG -> {
                TagKey<Item> tag = TargetResolver.tagKey(line.spec());
                yield tag != null && stack.is(tag);
            }
            case MOD -> TargetResolver.namespace(stack).equals(line.spec().id());
        };
    }

    /** true se alguma linha da lista aceita o item. */
    public static boolean anyMatches(TargetList list, ItemStack stack, boolean exact) {
        for (TargetLine line : list.lines()) {
            if (matches(line, stack, exact)) {
                return true;
            }
        }
        return false;
    }
}
