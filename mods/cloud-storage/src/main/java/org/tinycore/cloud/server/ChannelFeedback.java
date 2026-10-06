package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.integration.tcmine.CloudBackend;

import java.util.Locale;

/**
 * Resultado de criar/renomear canal, mostrado na tela do TC Cloud Link (o ordinal vai no pacote: só acrescentar no
 * fim). Vem do {@link PlayerChannels}.
 */
public enum ChannelFeedback {
    NONE,
    /** Pedido enviado, esperando a nuvem. */
    WORKING,
    CREATED,
    RENAMED,
    INVALID_NAME,
    DUPLICATE,
    LIMIT,
    /** Sem lease neste servidor (nuvem conectando, ocupada em outro servidor...). */
    NO_LEASE,
    /** A nuvem não tem a operação (TCMine sem os endpoints de canal). */
    UNSUPPORTED,
    /** Falha de rede ou canal que sumiu: tentar de novo. */
    FAILED;

    private static final ChannelFeedback[] VALUES = values();

    public static @NotNull ChannelFeedback byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    /** true se é um erro (a tela pinta de vermelho). */
    public boolean isError() {
        return ordinal() >= INVALID_NAME.ordinal();
    }

    public @NotNull String translationKey() {
        return "gui.tccloud.channel.feedback." + name().toLowerCase(Locale.ROOT);
    }

    static @NotNull ChannelFeedback of(@NotNull CloudBackend.ChannelRefusal refusal) {
        return switch (refusal) {
            case INVALID_NAME -> INVALID_NAME;
            case DUPLICATE -> DUPLICATE;
            case LIMIT -> LIMIT;
            case NO_LEASE -> NO_LEASE;
            case UNSUPPORTED -> UNSUPPORTED;
            case UNKNOWN_CHANNEL -> FAILED;
        };
    }
}
