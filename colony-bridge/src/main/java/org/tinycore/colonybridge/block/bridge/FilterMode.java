package org.tinycore.colonybridge.block.bridge;

/** Como a lista do filtro é usada. Configurado na aba "Filtro" da tela. */
public enum FilterMode {
    /** Filtro ignorado: qualquer item pode sair da rede. */
    OFF,
    /** Só os itens da lista podem sair da rede. */
    ALLOW,
    /** Os itens da lista nunca saem da rede. */
    BLOCK;

    private static final FilterMode[] VALUES = values();

    /** @param inList se o item candidato está na lista do filtro */
    public boolean allows(boolean inList) {
        return switch (this) {
            case OFF -> true;
            case ALLOW -> inList;
            case BLOCK -> !inList;
        };
    }

    public FilterMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** Converte número de NBT/pacote; valores inválidos viram {@link #OFF}. */
    public static FilterMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }

    public String translationKey() {
        return "filter.tccolonybridge." + name().toLowerCase();
    }
}
