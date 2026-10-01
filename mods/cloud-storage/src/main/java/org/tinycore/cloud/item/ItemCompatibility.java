package org.tinycore.cloud.item;

import org.jetbrains.annotations.NotNull;

/**
 * Situação de um item da nuvem NESTE servidor (plano §6.1). Só {@link #OK} pode sair da nuvem e aparecer
 * para a rede do AE2; os outros aparecem na tela apagados, com o motivo.
 */
public enum ItemCompatibility {
    /** Decodificou e a política permite. */
    OK,
    /** O mod do item não está carregado neste servidor. */
    MOD_MISSING,
    /** O mod existe, mas o item não (versão diferente do mod). */
    ITEM_MISSING,
    /** Algum dado do item não decodifica aqui (componente desconhecido, encantamento ausente...). */
    DATA_INVALID,
    /** A política da nuvem bloqueia este item agora. */
    BLOCKED;

    /** Chave de tradução do motivo (tooltip da tela). */
    public @NotNull String translationKey() {
        return "gui.tccloud.compat." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
