package org.tinycore.colonybridge.logic.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.tinycore.colonybridge.Config;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Estima quanto custa craftar um item, em "unidades de matéria-prima", seguindo as receitas (padrões)
 * cadastradas no AE2: custo = soma dos custos das entradas ÷ quantidade produzida. Um item sem padrão
 * (minério, madeira...) custa 1.
 * <p>
 * É uma <b>estimativa barata</b>, não o plano real do AE2 (que é assíncrono e caro): usa só o primeiro
 * padrão de cada item, desce no máximo {@code craftCostDepth} níveis e trata receitas circulares
 * (lingote ↔ bloco) como matéria-prima. Serve para ordenar candidatos, não para contabilidade.
 * <p>
 * Os resultados ficam em cache até {@link #clear()}, chamado a cada ciclo (os padrões da rede podem mudar).
 * Usado pelo {@link CraftCandidates}.
 */
final class CraftCost {

    /** Mapa chave → custo sem "boxing" de double (≈ Dictionary&lt;AEKey, double&gt; em C#). */
    private final Object2DoubleOpenHashMap<AEKey> cache = new Object2DoubleOpenHashMap<>();
    /** Itens na pilha de cálculo atual, para não entrar em loop em receitas circulares. */
    private final Set<AEKey> visiting = new HashSet<>();

    void clear() {
        cache.clear();
    }

    double of(ICraftingService crafting, AEKey key) {
        return compute(crafting, key, Config.CRAFT_COST_DEPTH.get());
    }

    private double compute(ICraftingService crafting, AEKey key, int depth) {
        if (cache.containsKey(key)) {
            return cache.getDouble(key);
        }
        if (depth <= 0 || visiting.contains(key)) {
            return 1; // limite de profundidade ou receita circular: conta como matéria-prima
        }
        Collection<IPatternDetails> patterns = crafting.getCraftingFor(key);
        if (patterns.isEmpty()) {
            cache.put(key, 1);
            return 1;
        }
        visiting.add(key);
        IPatternDetails pattern = patterns.iterator().next();
        double inputs = 0;
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            GenericStack[] options = input.getPossibleInputs();
            if (options.length == 0) {
                continue;
            }
            GenericStack first = options[0];
            inputs += compute(crafting, first.what(), depth - 1) * units(first) * input.getMultiplier();
        }
        visiting.remove(key);
        double cost = inputs / Math.max(1, producedUnits(pattern, key));
        cache.put(key, cost);
        return cost;
    }

    /** Quantidade em "unidades": itens contam 1 a 1, fluidos em baldes (o AE2 guarda fluido em mB). */
    private static double units(GenericStack stack) {
        return (double) stack.amount() / stack.what().getAmountPerUnit();
    }

    /** Quanto do item o padrão produz por execução (receitas como 1 tronco → 4 tábuas). */
    private static double producedUnits(IPatternDetails pattern, AEKey key) {
        for (GenericStack output : pattern.getOutputs()) {
            if (output.what().equals(key)) {
                return units(output);
            }
        }
        return 1;
    }
}
