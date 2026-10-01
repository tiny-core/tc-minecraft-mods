package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.CloudOp;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.JournalFile;
import org.tinycore.cloud.cloud.journal.JournalRecord;
import org.tinycore.cloud.cloud.journal.JournalReplay;
import org.tinycore.cloud.item.EncodedItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Grava no diário do mundo ({@code <mundo>/tccloud/journal.bin}) e mantém o {@code checkpoint.json}. É a única
 * classe do servidor que escreve nesses arquivos.
 *
 * <p>Regra: um lote só sai daqui (para o {@link BatchOutbox}) depois de gravado com fsync. Se a gravação falhar,
 * a exceção sobe e o {@link CloudService} deixa a nuvem em somente leitura: sem diário não há garantia contra
 * duplicação.
 */
final class JournalWriter {

    /** Lote pronto para envio, com as definições de item que o backend pode não ter. */
    record Outgoing(@NotNull Batch batch, @NotNull List<EncodedItem> definitions) {}

    private final JournalFile journal;
    private final Path checkpointFile;
    private final long compactBytes;
    private final Set<String> defined = new HashSet<>();
    private final Map<UUID, JournalReplay.SeqPosition> sealed = new HashMap<>();
    private Checkpoint checkpoint;

    JournalWriter(@NotNull Path folder, @NotNull Checkpoint checkpoint, long compactBytes) {
        this.journal = new JournalFile(folder.resolve("journal.bin"));
        this.checkpointFile = folder.resolve("checkpoint.json");
        this.checkpoint = checkpoint;
        this.compactBytes = compactBytes;
    }

    /** Lê o diário existente (boot). */
    @NotNull JournalReplay replay() throws IOException {
        JournalReplay replay = JournalReplay.of(journal.readAll());
        defined.addAll(replay.definitions().keySet());
        return replay;
    }

    /**
     * Grava os lotes (com as definições novas antes) e devolve o que deve ser enviado.
     *
     * @param definitionOf definição de um item pela impressão digital (todo item de um lote é conhecido)
     */
    @NotNull List<Outgoing> writeBatches(@NotNull List<Batch> batches, @NotNull Function<String, EncodedItem> definitionOf)
            throws IOException {
        if (batches.isEmpty()) return List.of();
        List<JournalRecord> records = new ArrayList<>();
        List<Outgoing> outgoing = new ArrayList<>(batches.size());
        for (Batch batch : batches) {
            List<EncodedItem> definitions = new ArrayList<>();
            for (CloudOp op : batch.ops()) {
                EncodedItem item = definitionOf.apply(op.key().fingerprint());
                if (item == null) throw new IllegalStateException("item sem definição no lote: " + op.key());
                if (op.delta() > 0) definitions.add(item); // o backend só precisa conhecer o que entra
                if (defined.add(item.fingerprint())) records.add(new JournalRecord.ItemDefined(item));
            }
            records.add(new JournalRecord.BatchWritten(batch));
            outgoing.add(new Outgoing(batch, definitions));
        }
        journal.append(records, true);
        for (Batch batch : batches) {
            sealed.merge(batch.playerUuid(), new JournalReplay.SeqPosition(batch.epoch(), batch.seq()),
                    (a, b) -> b.epoch() > a.epoch() || (b.epoch() == a.epoch() && b.seq() > a.seq()) ? b : a);
        }
        return outgoing;
    }

    /** Créditos ainda não duráveis de um jogador (informativo: sem fsync). */
    void writePending(@NotNull UUID player, @NotNull Map<BalanceKey, Long> credits) throws IOException {
        journal.append(List.of(new JournalRecord.PendingCredits(player, credits)), false);
    }

    void writeAck(@NotNull Batch batch) throws IOException {
        journal.append(List.of(new JournalRecord.Ack(batch.playerUuid(), batch.epoch(), batch.seq())), false);
    }

    /** Fim de um save: compacta se preciso, marca o save e atualiza o checkpoint. */
    void writeSaveMark() throws IOException {
        compactIfLarge();
        journal.append(List.of(new JournalRecord.SaveMark(System.currentTimeMillis())), true);
        writeCheckpoint();
    }

    /** Parada limpa: tudo o que importava já está durável. */
    void writeCleanShutdown() throws IOException {
        writeCheckpoint();
        journal.append(List.of(new JournalRecord.CleanShutdown(System.currentTimeMillis())), true);
    }

    @NotNull Checkpoint checkpoint() {
        return checkpoint;
    }

    private void writeCheckpoint() throws IOException {
        checkpoint = checkpoint.withLastSealed(sealed);
        checkpoint.write(checkpointFile);
    }

    private void compactIfLarge() throws IOException {
        Path path = journal.path();
        if (!Files.exists(path) || Files.size(path) < compactBytes) return;
        JournalReplay replay = JournalReplay.of(journal.readAll());
        journal.rewrite(replay.compacted());
        defined.clear();
        defined.addAll(replay.compacted().stream()
                .filter(r -> r instanceof JournalRecord.ItemDefined)
                .map(r -> ((JournalRecord.ItemDefined) r).item().fingerprint())
                .toList());
        TcCloud.LOG.debug("Diário da nuvem compactado: {} lotes pendentes.", replay.outbox().size());
    }

    /** Compacta já (boot, depois de reportar as operações em dúvida). */
    void compactNow(@Nullable JournalReplay replay) throws IOException {
        if (replay == null) return;
        journal.rewrite(replay.compacted());
    }
}
