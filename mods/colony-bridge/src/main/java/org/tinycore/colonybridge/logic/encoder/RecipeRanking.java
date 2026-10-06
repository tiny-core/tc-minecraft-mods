package org.tinycore.colonybridge.logic.encoder;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Escolhe a receita "mais barata" entre várias que produzem o mesmo item, para o TC Pattern Encoder. Regra pura
 * (genérica, sem itens), testada em JUnit:
 * <ol>
 *   <li>menos ingredientes que a rede ME não tem nem sabe craftar (o padrão sai utilizável já);</li>
 *   <li>menos ingredientes por item produzido (1 ingrediente para 4 itens ganha de 4 ingredientes para 1);</li>
 *   <li>empate: a primeira da lista (ordem estável entre varreduras).</li>
 * </ol>
 * É uma estimativa de um nível só, de propósito: o custo profundo ({@code CraftCost}) usa padrões do AE2, que é
 * justamente o que falta aqui.
 */
public final class RecipeRanking {

    private RecipeRanking() {}

    /**
     * @param missing  ingredientes sem estoque nem padrão na rede
     * @param inputs   total de ingredientes da receita
     * @param produced quantidade produzida por craft (mínimo 1)
     * @return índice da melhor, ou -1 com a lista vazia
     */
    public static <T> int best(List<T> candidates, ToIntFunction<T> missing, ToIntFunction<T> inputs,
                               ToIntFunction<T> produced) {
        int best = -1;
        int bestMissing = Integer.MAX_VALUE;
        double bestCost = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            T c = candidates.get(i);
            int m = missing.applyAsInt(c);
            double cost = (double) inputs.applyAsInt(c) / Math.max(1, produced.applyAsInt(c));
            if (m < bestMissing || (m == bestMissing && cost < bestCost)) {
                best = i;
                bestMissing = m;
                bestCost = cost;
            }
        }
        return best;
    }
}
