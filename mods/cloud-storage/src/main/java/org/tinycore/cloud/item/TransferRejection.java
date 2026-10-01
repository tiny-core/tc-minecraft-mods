package org.tinycore.cloud.item;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Por que um item não pode ENTRAR na nuvem (plano §6). Mostrado ao jogador na tela do TC Cloud Link. */
public enum TransferRejection {
    /** O Minecraft não conseguiu codificar o item. */
    ENCODE_FAILED,
    /** Dados grandes demais (livros enormes, NBT inflado). */
    TOO_LARGE,
    /** Regra do dono da nuvem. */
    POLICY,
    /** Item na tag {@code #tccloud:never_transfer}. */
    NEVER_TRANSFER,
    /** O item tem itens dentro: esvaziar antes. */
    NOT_EMPTY,
    /** Sinais de que o conteúdo mora no mundo; aguarda aprovação do dono. */
    WORLD_REFERENCE;

    public @NotNull String translationKey() {
        return "gui.tccloud.reject." + name().toLowerCase(Locale.ROOT);
    }
}
