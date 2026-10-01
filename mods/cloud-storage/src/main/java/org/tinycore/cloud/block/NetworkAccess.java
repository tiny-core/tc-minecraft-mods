package org.tinycore.cloud.block;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * O que a rede AE2 onde o TC Cloud Link está pode fazer com o canal (plano §7). O AE2 1.21 não tem mais o
 * Security Terminal: quem acessa a rede acessa tudo o que está montado nela, por isso o dono escolhe.
 */
public enum NetworkAccess {
    /** A rede não vê o canal; só a tela do Link guarda e retira. */
    TERMINAL_ONLY,
    /** A rede guarda no canal, mas não retira. */
    DEPOSIT_ONLY,
    /** A rede guarda e retira (padrão). */
    FULL;

    public boolean mounts() {
        return this != TERMINAL_ONLY;
    }

    public boolean allowsExtract() {
        return this == FULL;
    }

    public @NotNull NetworkAccess next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static @NotNull NetworkAccess byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : FULL;
    }

    public @NotNull String translationKey() {
        return "gui.tccloud.access." + name().toLowerCase(Locale.ROOT);
    }
}
