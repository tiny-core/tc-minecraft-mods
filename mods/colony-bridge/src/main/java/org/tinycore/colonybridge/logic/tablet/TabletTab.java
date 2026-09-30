package org.tinycore.colonybridge.logic.tablet;

/**
 * Abas do tablet, na ordem em que aparecem. O ordinal viaja em pacotes: só acrescentar valores no fim.
 * <p>
 * {@link #implemented} esconde as abas de etapas ainda não feitas (painéis na 10c, Chunk Loader na Fase 11),
 * para o jogador não ver botões que nunca funcionam.
 */
public enum TabletTab {
    TERMINAL(true),
    BRIDGE(true),
    SUPPLY(true),
    BRIDGE_PANEL(true),
    SUPPLY_PANEL(true),
    CHUNK_LOADER(false);

    private static final TabletTab[] VALUES = values();

    private final boolean implemented;

    TabletTab(boolean implemented) {
        this.implemented = implemented;
    }

    public boolean implemented() {
        return implemented;
    }

    /** Número vindo de pacote; inválido → null. */
    public static TabletTab byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
