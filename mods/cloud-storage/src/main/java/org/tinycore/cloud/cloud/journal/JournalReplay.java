package org.tinycore.cloud.cloud.journal;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudOp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lê o diário em ordem e reconstrói o que o boot precisa saber (função pura sobre a lista de registros):
 * <ul>
 *   <li>{@link #outbox()}: lotes gravados e ainda não confirmados, a reenviar ao TCMine na ordem;</li>
 *   <li>{@link #lastSealed()}: último (época, seq) gravado por jogador, que vai para o checkpoint;</li>
 *   <li>{@link #uncleanShutdown()} e {@link #doubtful()}: o servidor caiu sem parada limpa e o que pode
 *       ter se perdido por isso;</li>
 *   <li>{@link #compacted()}: o mínimo que precisa continuar no arquivo.</li>
 * </ul>
 */
public final class JournalReplay {

    private final List<Batch> outbox;
    private final Map<UUID, SeqPosition> lastSealed;
    private final boolean uncleanShutdown;
    private final List<DoubtfulOperation> doubtful;
    private final Map<UUID, JournalRecord.PendingCredits> latestPending;

    private JournalReplay(List<Batch> outbox, Map<UUID, SeqPosition> lastSealed, boolean uncleanShutdown,
                          List<DoubtfulOperation> doubtful, Map<UUID, JournalRecord.PendingCredits> latestPending) {
        this.outbox = outbox;
        this.lastSealed = lastSealed;
        this.uncleanShutdown = uncleanShutdown;
        this.doubtful = doubtful;
        this.latestPending = latestPending;
    }

    public static @NotNull JournalReplay of(@NotNull List<JournalRecord> records) {
        Map<String, Batch> unacked = new LinkedHashMap<>();
        Set<String> acked = new HashSet<>();
        Map<UUID, SeqPosition> lastSealed = new LinkedHashMap<>();
        Map<UUID, JournalRecord.PendingCredits> pending = new LinkedHashMap<>();
        List<Batch> sinceLastSave = new ArrayList<>();

        for (JournalRecord record : records) {
            switch (record) {
                case JournalRecord.BatchWritten(Batch b) -> {
                    String id = id(b.playerUuid(), b.epoch(), b.seq());
                    if (!acked.contains(id)) unacked.put(id, b);
                    lastSealed.merge(b.playerUuid(), new SeqPosition(b.epoch(), b.seq()), SeqPosition::max);
                    sinceLastSave.add(b);
                }
                case JournalRecord.Ack(UUID player, long epoch, long seq) -> {
                    String id = id(player, epoch, seq);
                    acked.add(id);
                    unacked.remove(id);
                }
                case JournalRecord.SaveMark ignored -> sinceLastSave.clear();
                case JournalRecord.PendingCredits p -> pending.put(p.playerUuid(), p);
                case JournalRecord.CleanShutdown ignored -> {
                    sinceLastSave.clear();
                    pending.clear(); // parada limpa = tudo durável
                }
            }
        }

        boolean unclean = !records.isEmpty() && !(records.getLast() instanceof JournalRecord.CleanShutdown);
        List<DoubtfulOperation> doubtful = unclean ? doubtful(pending, sinceLastSave) : List.of();
        return new JournalReplay(List.copyOf(unacked.values()), Collections.unmodifiableMap(lastSealed),
                unclean, doubtful, pending);
    }

    private static List<DoubtfulOperation> doubtful(Map<UUID, JournalRecord.PendingCredits> pending,
                                                    List<Batch> sinceLastSave) {
        List<DoubtfulOperation> result = new ArrayList<>();
        pending.values().forEach(p -> p.credits().forEach((key, amount) -> result.add(
                new DoubtfulOperation(p.playerUuid(), key, DoubtfulOperation.Kind.PENDING_CREDIT, amount))));
        for (Batch b : sinceLastSave) {
            for (CloudOp op : b.ops()) {
                if (op.isDebit()) {
                    result.add(new DoubtfulOperation(b.playerUuid(), op.key(),
                            DoubtfulOperation.Kind.RECENT_DEBIT, -op.delta()));
                }
            }
        }
        return List.copyOf(result);
    }

    /** Lotes a reenviar, na ordem em que foram gravados. */
    public @NotNull List<Batch> outbox() {
        return outbox;
    }

    /** Último lote gravado por jogador (independe de confirmação). */
    public @NotNull Map<UUID, SeqPosition> lastSealed() {
        return lastSealed;
    }

    public boolean uncleanShutdown() {
        return uncleanShutdown;
    }

    /** Vazio quando a última parada foi limpa. */
    public @NotNull List<DoubtfulOperation> doubtful() {
        return doubtful;
    }

    /**
     * Registros mínimos para continuar: lotes não confirmados (na ordem) e os créditos pendentes mais
     * recentes. Usado com {@link JournalFile#rewrite} para o arquivo não crescer para sempre; só chamar
     * depois de reportar as operações em dúvida, porque a compactação as apaga.
     */
    public @NotNull List<JournalRecord> compacted() {
        List<JournalRecord> keep = new ArrayList<>();
        outbox.forEach(b -> keep.add(new JournalRecord.BatchWritten(b)));
        latestPending.values().stream().filter(p -> !p.credits().isEmpty()).forEach(keep::add);
        return keep;
    }

    private static String id(UUID player, long epoch, long seq) {
        return player + "/" + epoch + "/" + seq;
    }

    /** Posição de um lote: época do lease e sequência dentro dela. */
    public record SeqPosition(long epoch, long seq) {
        static SeqPosition max(SeqPosition a, SeqPosition b) {
            if (a.epoch != b.epoch) return a.epoch > b.epoch ? a : b;
            return a.seq >= b.seq ? a : b;
        }
    }

    /** Créditos pendentes mais recentes de um jogador (vazio se nenhum). */
    public @NotNull Map<BalanceKey, Long> pendingCredits(@NotNull UUID player) {
        JournalRecord.PendingCredits p = latestPending.get(player);
        return p == null ? Map.of() : p.credits();
    }
}
