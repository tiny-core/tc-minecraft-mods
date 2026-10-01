package org.tinycore.cloud.cloud.journal;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.cloud.BalanceKey;

import java.util.UUID;

/**
 * Operação que pode ter se perdido num crash: o mod não tem como saber se o mundo gravou o item. Vai
 * para o painel do TCMine ("Em dúvida") e só o dono decide se devolve; nunca é devolvida sozinha, porque
 * devolver às cegas duplicaria quando o mundo já tinha gravado o item.
 */
public record DoubtfulOperation(@NotNull UUID playerUuid, @NotNull BalanceKey key, @NotNull Kind kind, long amount) {

    public enum Kind {
        /** Item saiu do mundo para a nuvem, mas o crédito ainda não era durável: talvez sumiu dos dois. */
        PENDING_CREDIT,
        /** Item saiu da nuvem depois do último save: talvez não chegou ao disco do mundo. */
        RECENT_DEBIT
    }
}
