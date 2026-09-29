package org.tinycore.colonybridge.logic.crafting;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * As regras de "qual candidato vem primeiro" do craft por tag, separadas do {@link CraftCandidates} para
 * poderem ser testadas sem o jogo carregado (os testes usam textos no lugar de itens do AE2).
 * <p>
 * Ordem: (1) mods marcados primeiro, se o modo for {@link ModFilterMode#PREFER}; (2) a
 * {@link CraftPreference} — custo crescente, decrescente ou posição na lista de preferidos (fora da lista
 * vai para o fim, do mais barato ao mais caro); (3) id do item, como desempate estável.
 * <p>
 * {@code <T>} é o tipo do candidato (no jogo, {@code AEItemKey}); as funções dizem como ler id, mod e custo
 * dele. {@code ToDoubleFunction<T>} ≈ {@code Func<T, double>} em C#.
 */
final class CraftOrdering {

    static final String VANILLA = "minecraft";

    private CraftOrdering() {}

    /** true se as regras de mod (só vanilla do servidor, modo da ponte) deixam escolher um item deste mod. */
    static boolean modAllowed(CraftRules rules, String mod) {
        return (!rules.vanillaOnly() || VANILLA.equals(mod)) && rules.modMode().allows(mod, rules.mods());
    }

    static <T> Comparator<T> comparator(CraftRules rules, Function<T, String> id, Function<T, String> mod,
                                        ToDoubleFunction<T> cost) {
        Comparator<T> cheapest = Comparator.comparingDouble(cost);
        Comparator<T> byPreference = switch (rules.preference()) {
            case CHEAPEST -> cheapest;
            case MOST_EXPENSIVE -> cheapest.reversed();
            case LIST -> Comparator.<T>comparingInt(t -> listIndex(rules, id.apply(t))).thenComparing(cheapest);
        };
        if (rules.modMode() == ModFilterMode.PREFER) {
            // false vem antes de true: itens de mods marcados (não "fora da lista") primeiro.
            byPreference = Comparator.<T, Boolean>comparing(t -> !rules.mods().contains(mod.apply(t)))
                    .thenComparing(byPreference);
        }
        return byPreference.thenComparing(id);
    }

    /** Posição do item na lista de preferidos; fora da lista vai para o fim. */
    private static int listIndex(CraftRules rules, String itemId) {
        int index = rules.preferredIds().indexOf(itemId);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}
