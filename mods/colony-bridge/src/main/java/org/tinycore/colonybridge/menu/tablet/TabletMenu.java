package org.tinycore.colonybridge.menu.tablet;

import org.jetbrains.annotations.Nullable;

/**
 * Menu que pode ser aberto pelo tablet (Terminal, Ponte, Abastecedor). A tela usa {@link #tabletView()} para
 * decidir se desenha a barra de abas; o servidor usa o mesmo dado para saber em que aba o jogador está.
 */
public interface TabletMenu {

    /** Abas do tablet, ou null se a tela foi aberta pelo bloco. */
    @Nullable TabletView tabletView();
}
