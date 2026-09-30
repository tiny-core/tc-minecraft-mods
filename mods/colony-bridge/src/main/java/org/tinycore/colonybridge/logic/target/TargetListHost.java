package org.tinycore.colonybridge.logic.target;

import org.jetbrains.annotations.Nullable;

/**
 * Bloco que tem listas de linhas editáveis pela tela (Abastecedor: Manter e Excedente; Ponte: filtro).
 * Assim o pacote de edição e a sincronização com a tela ({@code menu/TargetListEditor},
 * {@code menu/TargetListSync}) funcionam para qualquer bloco, sem conhecer cada um.
 */
public interface TargetListHost {

    /** A lista deste tipo, ou null se o bloco não tem essa lista (pacote pedindo lista errada). */
    @Nullable TargetList targetList(TargetListKind kind);

    /** Uma lista mudou: marcar para salvar e rodar um ciclo logo, para a tela refletir a mudança. */
    void onTargetListChanged(TargetListKind kind);
}
