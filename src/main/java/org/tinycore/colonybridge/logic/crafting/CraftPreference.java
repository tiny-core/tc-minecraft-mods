package org.tinycore.colonybridge.logic.crafting;

/**
 * Como escolher o item a craftar quando um pedido aceita vários (tag, ferramenta, comida).
 * Configurado em {@code tagCraftPreference} (config do servidor); usado pelo {@link CraftCandidates}.
 * O custo vem do {@link CraftCost}: estimativa de matérias-primas pelas receitas (padrões) do AE2.
 */
public enum CraftPreference {
    /** O de menor custo estimado (ex.: picareta de pedra antes da de diamante). */
    CHEAPEST,
    /** O de maior custo estimado (ex.: a melhor ferramenta que a rede sabe fazer). */
    MOST_EXPENSIVE,
    /**
     * Na ordem da lista {@code tagCraftPreferredItems}; itens fora da lista vêm depois, do mais
     * barato para o mais caro.
     */
    LIST
}
