package org.tinycore.colonybridge.logic.supply;

/**
 * Regra pura do Abastecedor: quanto uma linha da lista deve mover neste ciclo. Não conhece item,
 * armazém nem rede ME — só números — para poder ser testada sem o jogo.
 * <p>
 * Quem executa o movimento é o {@link SupplyLogic}; aqui só se decide a quantidade.
 */
final class SupplyRule {

    private SupplyRule() {}

    /**
     * Linha "manter no armazém": quanto tirar da rede ME para chegar à meta.
     *
     * @param current  quantidade no armazém agora
     * @param target   meta da linha
     * @param perCycle teto de itens movidos por linha em cada ciclo (config)
     * @return 0 se já está na meta (ou acima)
     */
    static long restock(long current, long target, long perCycle) {
        return clamp(target - current, perCycle);
    }

    /**
     * Linha "excedente para o ME": quanto devolver à rede. Nada sai se a colônia está pedindo o item,
     * senão a ponte entregaria e o Abastecedor levaria de volta (vaivém sem fim).
     *
     * @param requested true se algum pedido em aberto da colônia aceita este item
     * @return 0 se não passa da meta ou se o item está em pedido
     */
    static long surplus(long current, long target, boolean requested, long perCycle) {
        return requested ? 0 : clamp(current - target, perCycle);
    }

    private static long clamp(long amount, long perCycle) {
        return Math.max(0, Math.min(amount, perCycle));
    }
}
