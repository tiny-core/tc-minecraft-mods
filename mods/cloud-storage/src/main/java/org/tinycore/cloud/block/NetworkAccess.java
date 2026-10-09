package org.tinycore.cloud.block;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * O que a rede AE2 onde o TC Cloud Link está pode fazer com o canal (plano §7). O AE2 1.21 não tem mais o
 * Security Terminal: quem acessa a rede acessa tudo o que está montado nela, por isso o dono escolhe.
 *
 * <p>O ordinal é salvo no bloco e vai no pacote da tela: valores novos só no fim. A ordem do botão é a de
 * {@link #next} (do mais fechado ao mais aberto), não a do enum.
 */
public enum NetworkAccess {
    /** A rede não vê o canal; só a tela do Link guarda e retira. */
    TERMINAL_ONLY,
    /** A rede guarda no canal, mas não retira. */
    DEPOSIT_ONLY,
    /** A rede guarda e retira (padrão). */
    FULL,
    /** A rede vê e retira, mas não guarda: nada entra na nuvem por automação (importadores, sobra de craft...). */
    EXTRACT_ONLY;

    public boolean mounts() {
        return this != TERMINAL_ONLY;
    }

    /** A rede vê o que está na nuvem e pode tirar. */
    public boolean allowsExtract() {
        return this == FULL || this == EXTRACT_ONLY;
    }

    /** A rede pode guardar na nuvem. */
    public boolean allowsInsert() {
        return this == FULL || this == DEPOSIT_ONLY;
    }

    /** Próximo modo no botão: só a tela → só guardar → só retirar → guardar e retirar → só a tela. */
    public @NotNull NetworkAccess next() {
        return switch (this) {
            case TERMINAL_ONLY -> DEPOSIT_ONLY;
            case DEPOSIT_ONLY -> EXTRACT_ONLY;
            case EXTRACT_ONLY -> FULL;
            case FULL -> TERMINAL_ONLY;
        };
    }

    public static @NotNull NetworkAccess byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : FULL;
    }

    public @NotNull String translationKey() {
        return "gui.tccloud.access." + name().toLowerCase(Locale.ROOT);
    }
}
