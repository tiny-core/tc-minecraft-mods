package org.tinycore.colonybridge.menu;

import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.target.TargetListHost;
import org.tinycore.colonybridge.logic.target.TargetListKind;

/**
 * Menu que mostra listas de linhas (Abastecedor, Ponte). Deixa o pacote de edição ({@code ModNetwork}), o
 * pacote de sincronização ({@code ClientPayloadHandler}) e o JEI tratarem as duas telas do mesmo jeito.
 */
public interface TargetListMenu {

    /** Listas recebidas (cliente) / controle de envio (servidor). */
    TargetListSync targetLists();

    /** Só no servidor: o bloco dono das listas (null no cliente). */
    @Nullable TargetListHost listHost();

    /** Lista da aba aberta: destino do shift-clique no inventário. */
    void setActiveList(TargetListKind kind);
}
