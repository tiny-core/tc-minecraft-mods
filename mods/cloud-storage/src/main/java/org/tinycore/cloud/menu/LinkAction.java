package org.tinycore.cloud.menu;

import org.jetbrains.annotations.Nullable;

/**
 * Pedidos que a tela do TC Cloud Link manda ao servidor. O cliente só diz O QUE quer; quanto sai, se cabe e se
 * pode, quem decide é o servidor ({@link CloudLinkActions}).
 */
public enum LinkAction {
    /** Clique esquerdo num item da grade: até um stack para o inventário. */
    TAKE_STACK,
    /** Clique direito num item da grade: 1 unidade para o inventário. */
    TAKE_ONE,
    /** Clique esquerdo na grade com item no cursor: guarda tudo. */
    DEPOSIT_CARRIED,
    /** Clique direito na grade com item no cursor: guarda 1. */
    DEPOSIT_ONE,
    /** Botão do modo de acesso da rede AE2. */
    CYCLE_ACCESS,
    PRIORITY_UP,
    PRIORITY_DOWN,
    /** Escolhe o canal do Link (texto = id do canal). */
    SELECT_CHANNEL,
    /** Cria um canal (texto = nome) e passa o Link para ele. */
    CREATE_CHANNEL,
    /** Renomeia o canal do Link (texto = nome novo). */
    RENAME_CHANNEL;

    public static @Nullable LinkAction byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }
}
