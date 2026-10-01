package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

/**
 * Uma mudança de saldo que já ficou durável e vai para o TCMine dentro de um {@link Batch}.
 *
 * @param key   canal + item
 * @param delta positivo = crédito (entrou na nuvem), negativo = débito (saiu); nunca zero
 */
public record CloudOp(@NotNull BalanceKey key, long delta) {

    public CloudOp {
        if (delta == 0) throw new IllegalArgumentException("delta zero não é operação");
    }

    public boolean isDebit() {
        return delta < 0;
    }
}
