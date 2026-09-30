package org.tinycore.colonybridge.menu.bridge;

/**
 * Abas da tela da ponte. Fica no pacote do menu (e não só na tela) porque o menu do servidor também
 * precisa saber a aba aberta: ela decide para qual grupo de ghost slots vai o shift-clique.
 */
public enum BridgeTab {
    /** Estado, resumo dos pedidos, crafting on/off, redstone e preferência de craft. */
    GENERAL,
    /** Filtro de itens (ghost slots + inventário). */
    FILTER,
    /** Itens preferidos para o craft por tag (ghost slots + inventário). */
    PREFERRED,
    /** Mods que valem para o craft por tag. */
    MODS;

    private static final BridgeTab[] VALUES = values();

    /** Número vindo de pacote; inválido vira {@link #GENERAL}. */
    public static BridgeTab byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : GENERAL;
    }

    /** true se a aba mostra o inventário do jogador (para pegar itens e clicar nos ghost slots). */
    public boolean showsInventory() {
        return this == GENERAL || this == FILTER || this == PREFERRED;
    }
}
