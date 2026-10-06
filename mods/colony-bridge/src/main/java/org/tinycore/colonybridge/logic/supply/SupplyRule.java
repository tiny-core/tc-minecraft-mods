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

    /**
     * Linha "manter": quanto pedir de craft ao AE2. Só pede quando, depois de trazer o que havia, o armazém continua
     * abaixo da meta e a rede ME <b>não tem mais nada</b> da linha (assim nunca se crafta o que já existe), e quando
     * não há craft da linha rodando (senão cada ciclo pediria outro).
     *
     * @param network quanto a rede tem da linha depois do movimento deste ciclo
     * @param busy    true se já há craft (ou cálculo) de algum item da linha
     * @return 0 = não pedir; senão a falta inteira (o teto por job fica com quem pede o craft)
     */
    static long craft(boolean enabled, long current, long target, long network, boolean busy) {
        if (!enabled || busy || network > 0 || current >= target) {
            return 0;
        }
        return target - current;
    }

    /**
     * Divide uma quantidade entre várias fontes, na ordem dada (quem chama ordena: a de mais estoque
     * primeiro). Usado nas linhas de tag e de mod: "trazer 40 de {@code #c:ingots/iron}" tira do item que a
     * rede tem mais e, se não bastar, do seguinte.
     *
     * @param wanted    total a mover
     * @param available quanto cada fonte tem (valores negativos contam como 0)
     * @return quanto tirar de cada fonte (mesmo tamanho de {@code available}; soma ≤ {@code wanted})
     */
    static long[] allocate(long wanted, long[] available) {
        long[] take = new long[available.length];
        long remaining = Math.max(0, wanted);
        for (int i = 0; i < available.length && remaining > 0; i++) {
            take[i] = Math.min(remaining, Math.max(0, available[i]));
            remaining -= take[i];
        }
        return take;
    }

    private static long clamp(long amount, long perCycle) {
        return Math.max(0, Math.min(amount, perCycle));
    }
}
