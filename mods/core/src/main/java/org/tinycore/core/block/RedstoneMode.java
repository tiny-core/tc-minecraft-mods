package org.tinycore.core.block;

/** Como a ponte reage a sinal de redstone. Configurado na tela da ponte. */
public enum RedstoneMode {
    /** Funciona sempre. */
    IGNORED,
    /** Só funciona com sinal de redstone. */
    ACTIVE_WITH_SIGNAL,
    /** Só funciona sem sinal de redstone. */
    ACTIVE_WITHOUT_SIGNAL;

    private static final RedstoneMode[] VALUES = values();

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case ACTIVE_WITH_SIGNAL -> powered;
            case ACTIVE_WITHOUT_SIGNAL -> !powered;
        };
    }

    /** Próximo modo (o botão da tela percorre os modos em ciclo). */
    public RedstoneMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** Converte um número vindo de NBT ou de pacote; valores inválidos viram {@link #IGNORED}. */
    public static RedstoneMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : IGNORED;
    }

    public String translationKey() {
        return "redstone.tccore." + name().toLowerCase();
    }
}
