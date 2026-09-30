package org.tinycore.colonybridge.logic.loader;

/**
 * Situação do Chunk Loader, decidida pela {@link LoaderRule} e mostrada na tela e no tablet. Cada valor tem uma
 * chave de tradução ({@link #translationKey}). O ordinal viaja no pacote da tela: só acrescentar no fim.
 */
public enum LoaderState {
    /** O admin desligou os loaders na config do servidor. */
    DISABLED_BY_ADMIN,
    /** O jogador desligou (botão, tablet ou redstone). */
    OFF,
    /** Fora de colônia, sem permissão ou duplicado na colônia. */
    NO_COLONY,
    /** Área carregada: algum membro da colônia está online. */
    LOADING,
    /** Área carregada, contando o tempo desde que o último membro saiu. */
    COUNTDOWN,
    /** Rede ME sem energia (ou sem canal): só o chunk do bloco fica carregado. */
    NO_POWER,
    /** A contagem acabou: só o chunk do bloco fica carregado, esperando um membro voltar. */
    SLEEPING;

    private static final LoaderState[] VALUES = values();

    /** A área da colônia fica carregada. */
    public boolean loadsArea() {
        return this == LOADING || this == COUNTDOWN;
    }

    /** O chunk do próprio bloco fica carregado (para ele perceber quando um membro voltar). */
    public boolean keepsOwnChunk() {
        return this == LOADING || this == COUNTDOWN || this == NO_POWER || this == SLEEPING;
    }

    public String translationKey() {
        return "gui.tccolonybridge.loader.state." + name().toLowerCase();
    }

    public static LoaderState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }
}
