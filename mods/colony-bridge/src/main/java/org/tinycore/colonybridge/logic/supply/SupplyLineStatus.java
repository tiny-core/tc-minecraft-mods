package org.tinycore.colonybridge.logic.supply;

import java.util.Locale;

/**
 * Situação de uma linha do Abastecedor em palavras, para o monitor dizer <b>o que está acontecendo</b>
 * (e não só números): trazendo do ME, falta na rede, retido porque a colônia pediu...
 * <p>
 * A decisão ({@link #of}) é uma regra pura, testada sem o jogo. A cor vem da {@link Severity}, que o
 * cliente traduz para as cores da marca.
 */
public enum SupplyLineStatus {
    /** Sem dados (bloco parado, sem colônia etc.). */
    UNKNOWN(Severity.NEUTRAL),
    /** Manter: o armazém já tem pelo menos a meta. */
    STOCKED(Severity.OK),
    /** Manter: trouxe itens da rede ME neste ciclo. */
    RESTOCKING(Severity.ACTIVE),
    /** Manter: falta no armazém e a rede ME não tem o item. */
    NETWORK_EMPTY(Severity.PROBLEM),
    /** Manter: a rede tem o item, mas nada coube nos racks. */
    WAREHOUSE_FULL(Severity.PROBLEM),
    /** Excedente: o armazém está dentro do limite. */
    WITHIN_LIMIT(Severity.OK),
    /** Excedente: mandou itens para a rede ME neste ciclo. */
    RETURNING(Severity.ACTIVE),
    /** Excedente: passou do limite, mas a colônia pediu o item, então ele fica (evita vaivém com a ponte). */
    HELD_REQUESTED(Severity.WARNING),
    /** Excedente: passou do limite e a rede ME não aceitou nada. */
    NETWORK_FULL(Severity.PROBLEM);

    /** Gravidade, usada para a cor e para contar "precisam de atenção". */
    public enum Severity { NEUTRAL, OK, ACTIVE, WARNING, PROBLEM }

    private final Severity severity;

    SupplyLineStatus(Severity severity) {
        this.severity = severity;
    }

    public Severity severity() {
        return severity;
    }

    /** true se o jogador precisa fazer algo (ou ao menos saber): aviso ou problema. */
    public boolean needsAttention() {
        return severity == Severity.WARNING || severity == Severity.PROBLEM;
    }

    /** Chave de tradução do texto curto mostrado no monitor. */
    public String translationKey() {
        return "monitor.tccolonybridge.supply.status." + name().toLowerCase(Locale.ROOT);
    }

    public static SupplyLineStatus byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : UNKNOWN;
    }

    /**
     * Situação depois do ciclo.
     *
     * @param keep      true = linha "manter no armazém"; false = "excedente para o ME"
     * @param warehouse quanto há no armazém depois do movimento
     * @param network   quanto há na rede ME
     * @param moved     quanto a linha moveu neste ciclo (em qualquer direção)
     * @param requested true se a colônia tem pedido em aberto que aceita o item
     */
    public static SupplyLineStatus of(boolean keep, long warehouse, long target, long network, long moved,
                                      boolean requested) {
        if (keep) {
            if (moved > 0) {
                return RESTOCKING;
            }
            if (warehouse >= target) {
                return STOCKED;
            }
            return network <= 0 ? NETWORK_EMPTY : WAREHOUSE_FULL;
        }
        if (moved > 0) {
            return RETURNING;
        }
        if (warehouse <= target) {
            return WITHIN_LIMIT;
        }
        return requested ? HELD_REQUESTED : NETWORK_FULL;
    }
}
