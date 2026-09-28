package org.tinycore.colonybridge.logic.bridge;

/**
 * O que a ponte fez (ou por que não fez nada) com um pedido no último ciclo.
 * Aparece na tela da ponte para o jogador entender por que um pedido não foi atendido.
 */
public enum RequestOutcome {
    /** Itens colocados no armazém neste ciclo. */
    DELIVERED,
    /** Já entregue antes; aguardando o courier levar (cooldown de reentrega). */
    WAITING_COURIER,
    /** Craft iniciado neste ciclo. */
    CRAFT_STARTED,
    /** Craft desta ponte ainda em andamento no AE2. */
    CRAFTING,
    /** Outra ponte está craftando para este pedido. */
    OTHER_BRIDGE,
    /** Item existe na rede, mas os racks do armazém estão cheios. */
    RACKS_FULL,
    /** Não há na rede e o pedido é por tag/ferramenta/comida com o craft por tag desligado ({@code tagCrafting}). */
    NO_STOCK,
    /**
     * Sem receita no AE2, na blacklist ou em espera após falha de craft. Em pedido por tag: nenhum item
     * craftável que o pedido aceite passou pelo filtro, pela config "só vanilla" e pelas esperas.
     */
    NOT_CRAFTABLE,
    /** O filtro da ponte não deixa este item sair da rede. */
    FILTERED,
    /** Não há na rede e o crafting está desligado nas configurações da ponte. */
    CRAFTING_DISABLED,
    /** Limite de pedidos por ciclo atingido; será tratado num próximo ciclo. */
    QUEUED,
    /**
     * O armazém já tem o suficiente: nada sai da rede, a ponte só pede ao MineColonies para reatribuir.
     * Fica no fim do enum porque o ordinal vai no pacote para o cliente (RequestLine/MonitorLine).
     */
    IN_WAREHOUSE;

    public String translationKey() {
        return "outcome.tccolonybridge." + name().toLowerCase();
    }

    /** true se o pedido "gastou" uma vaga do limite {@code maxRequestsPerCycle}. */
    boolean countsTowardLimit() {
        return this == DELIVERED || this == CRAFT_STARTED || this == IN_WAREHOUSE;
    }
}
