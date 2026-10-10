package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;

/**
 * Regra pura dos nomes de canal (criar e renomear), aplicada no servidor de jogo antes de pedir à nuvem e de novo
 * pela nuvem local. O cliente nunca decide: o texto que chega dele é tratado como hostil.
 */
public final class ChannelNames {

    public static final int MAX_LENGTH = 32;

    private ChannelNames() {}

    /**
     * Nome limpo: sem espaços nas pontas nem repetidos, sem caracteres de controle nem códigos de cor ({@code §}).
     *
     * @return null se ficou vazio, passou de {@link #MAX_LENGTH} ou tinha caractere proibido
     */
    public static @Nullable String clean(@NotNull String raw) {
        String name = raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_LENGTH) return null;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || c == '§') return null;
        }
        return name;
    }

    /** true se {@code name} já é usado por outro canal (maiúsculas e minúsculas não contam). */
    public static boolean taken(@NotNull Collection<String> otherNames, @NotNull String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (String other : otherNames) {
            if (other.toLowerCase(Locale.ROOT).equals(wanted)) return true;
        }
        return false;
    }
}
