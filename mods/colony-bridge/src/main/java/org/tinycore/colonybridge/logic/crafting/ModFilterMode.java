package org.tinycore.colonybridge.logic.crafting;

import java.util.Set;

/**
 * Como a lista de mods marcados na ponte afeta a escolha do item a craftar para pedidos por tag.
 * Usado pelo {@link CraftCandidates} através das {@link CraftRules}.
 */
public enum ModFilterMode {
    /** Todos os mods valem; a lista é ignorada. */
    ALL,
    /** Só itens dos mods marcados. */
    ONLY,
    /** Todos, menos os mods marcados. */
    EXCEPT,
    /** Todos valem, mas itens dos mods marcados vêm primeiro. */
    PREFER;

    private static final ModFilterMode[] VALUES = values();

    /** Converte o número vindo do NBT ou de um pacote; valor inválido vira {@link #ALL}, nunca erro. */
    public static ModFilterMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ALL;
    }

    public ModFilterMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public String translationKey() {
        return "gui.tccolonybridge.mod_mode." + name().toLowerCase();
    }

    /** true se um item do mod {@code modId} pode ser escolhido. */
    boolean allows(String modId, Set<String> marked) {
        return switch (this) {
            case ONLY -> marked.contains(modId);
            case EXCEPT -> !marked.contains(modId);
            case ALL, PREFER -> true;
        };
    }
}
