package org.tinycore.cloud.item.policy;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Uma regra de item definida pelo dono da nuvem no painel do TCMine ({@code cloud_item_rules}).
 *
 * @param scope   a que a regra se aplica
 * @param pattern {@code mod:item} para {@link Scope#ITEM}, {@code mod} para {@link Scope#MOD},
 *                {@code mod:caminho} (sem {@code #}) para {@link Scope#TAG}
 * @param action  permitir ou bloquear
 */
public record ItemRule(@NotNull Scope scope, @NotNull String pattern, @NotNull Action action) {

    public enum Scope {
        ITEM, TAG, MOD
    }

    public enum Action {
        ALLOW, BLOCK
    }

    public ItemRule {
        pattern = pattern.trim().toLowerCase(Locale.ROOT);
        if (pattern.startsWith("#")) pattern = pattern.substring(1);
        if (pattern.isEmpty()) throw new IllegalArgumentException("padrão vazio");
    }
}
