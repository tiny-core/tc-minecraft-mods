package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Situação da nuvem de um jogador neste servidor, mostrada na tela do TC Cloud Link. */
public enum CloudStatus {
    /** Nuvem desligada neste servidor (sem backend configurado). */
    DISABLED,
    /** Pedindo o canal ao backend. */
    CONNECTING,
    /** Canal em uso: pode guardar e retirar. */
    ACTIVE,
    /** Canal visível, mas nada entra nem sai (congelado, rollback, diário com problema). */
    READ_ONLY,
    /** Outro servidor está com o canal (o jogador saiu de lá há pouco ou está lá). */
    BUSY,
    /** Backend fora do ar: tentando de novo. */
    UNAVAILABLE;

    public boolean canUse() {
        return this == ACTIVE;
    }

    public @NotNull String translationKey() {
        return "gui.tccloud.status." + name().toLowerCase(Locale.ROOT);
    }
}
