package org.tinycore.colonybridge.logic.terminal;

import org.jetbrains.annotations.Nullable;

/**
 * O que um clique na grade do Terminal do Armazém pede ao servidor. O cliente só escolhe a ação; o
 * servidor confere tudo de novo (o item ainda existe? a mão está vazia?) antes de mover qualquer coisa.
 */
public enum TerminalAction {
    /** Clique esquerdo com a mão vazia: um stack vai para o cursor. */
    TAKE_STACK,
    /** Clique direito com a mão vazia: meio stack vai para o cursor. */
    TAKE_HALF,
    /** Shift + clique: um stack vai direto para o inventário. */
    TAKE_TO_INVENTORY,
    /** Clique esquerdo com item no cursor: guarda tudo no armazém. */
    INSERT_CARRIED,
    /** Clique direito com item no cursor: guarda um só. */
    INSERT_ONE;

    /** Ação pelo número vindo do pacote; null se fora da faixa (pacote inválido). */
    public static @Nullable TerminalAction byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
