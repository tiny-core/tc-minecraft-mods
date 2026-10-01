package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Numera os lotes de um jogador e mantém a <b>visão durável</b> dos saldos (o que o TCMine terá depois de
 * aplicar todos os lotes já gerados). É dela que saem os saldos esperados de cada {@link Batch}.
 *
 * <p>A visão durável nunca pode ficar negativa: se ficasse, haveria um débito de algo que o banco não tem
 * (bug no netting). Nesse caso lança exceção em vez de gerar um lote que o TCMine recusaria.
 */
public final class BatchSequencer {

    private final UUID playerUuid;
    private final long epoch;
    private long lastSeq;
    private final Map<BalanceKey, Long> durable;

    /**
     * @param lastSeq  último seq já gerado nesta época (0 num lease novo; maior ao retomar pelo diário)
     * @param snapshot saldos que o TCMine devolveu no acquire
     */
    public BatchSequencer(@NotNull UUID playerUuid, long epoch, long lastSeq, @NotNull Map<BalanceKey, Long> snapshot) {
        this.playerUuid = playerUuid;
        this.epoch = epoch;
        this.lastSeq = lastSeq;
        this.durable = new HashMap<>(snapshot);
    }

    /** Gera o próximo lote com as operações dadas; vazio se não houver operação. */
    public @NotNull Optional<Batch> seal(@NotNull List<CloudOp> ops) {
        if (ops.isEmpty()) return Optional.empty();
        Map<BalanceKey, Long> expected = new LinkedHashMap<>();
        for (CloudOp op : ops) {
            long after = durable.getOrDefault(op.key(), 0L) + op.delta();
            if (after < 0) {
                throw new IllegalStateException("saldo durável negativo em " + op.key() + ": " + after);
            }
            if (after == 0) durable.remove(op.key());
            else durable.put(op.key(), after);
            expected.put(op.key(), after);
        }
        lastSeq++;
        return Optional.of(new Batch(playerUuid, epoch, lastSeq, ops, expected));
    }

    public long durable(@NotNull BalanceKey key) {
        return durable.getOrDefault(key, 0L);
    }

    public long lastSeq() {
        return lastSeq;
    }

    public long epoch() {
        return epoch;
    }
}
