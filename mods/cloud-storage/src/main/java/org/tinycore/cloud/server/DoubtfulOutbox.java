package org.tinycore.cloud.server;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operações em dúvida esperando o TCMine confirmar o recebimento, uma por arquivo
 * ({@code <mundo>/tccloud/doubtful-<reportId>.json}).
 *
 * <p>Por que existe: no boot, o diário é compactado depois de lido, e a compactação apaga o que mostrava as
 * operações em dúvida. Se o envio falhasse (TCMine fora do ar), a informação se perderia. Agora ela vai para
 * este arquivo ANTES da compactação e só sai quando o TCMine confirma. O {@code reportId} vai junto: reenviar o
 * mesmo relatório não duplica a fila do dono.
 */
final class DoubtfulOutbox {

    private static final Gson GSON = new Gson();
    private static final String PREFIX = "doubtful-";

    /** Um relatório pendente. */
    record Report(@NotNull String reportId, @NotNull List<DoubtfulOperation> operations, @NotNull Path file) {}

    private record Row(String player, String channel, String fingerprint, String kind, long amount) {}

    private record Stored(String reportId, List<Row> operations) {}

    private final Path folder;

    DoubtfulOutbox(@NotNull Path folder) {
        this.folder = folder;
    }

    /** Grava um relatório novo e o devolve (gravação atômica: temporário + troca). */
    @NotNull Report add(@NotNull List<DoubtfulOperation> operations) throws IOException {
        String reportId = UUID.randomUUID().toString();
        List<Row> rows = operations.stream().map(op -> new Row(op.playerUuid().toString(), op.key().channelId().toString(),
                op.key().fingerprint(), op.kind().name(), op.amount())).toList();
        Files.createDirectories(folder);
        Path file = folder.resolve(PREFIX + reportId + ".json");
        Path tmp = folder.resolve(PREFIX + reportId + ".json.tmp");
        Files.writeString(tmp, GSON.toJson(new Stored(reportId, rows)), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new Report(reportId, operations, file);
    }

    /** Relatórios ainda não confirmados (de boots anteriores ou deste). Arquivo ilegível é deixado de lado com aviso. */
    @NotNull List<Report> pending() {
        List<Report> reports = new ArrayList<>();
        if (!Files.isDirectory(folder)) return reports;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, PREFIX + "*.json")) {
            for (Path file : files) {
                try {
                    Stored stored = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
                    List<DoubtfulOperation> ops = stored.operations().stream().map(r -> new DoubtfulOperation(
                            UUID.fromString(r.player()), new BalanceKey(UUID.fromString(r.channel()), r.fingerprint()),
                            DoubtfulOperation.Kind.valueOf(r.kind()), r.amount())).toList();
                    reports.add(new Report(stored.reportId(), ops, file));
                } catch (IOException | RuntimeException e) {
                    TcCloud.LOG.warn("Nuvem: relatório de operações em dúvida ilegível em {}: {}", file, e.toString());
                }
            }
        } catch (IOException e) {
            TcCloud.LOG.warn("Nuvem: não consegui listar os relatórios pendentes em {}: {}", folder, e.toString());
        }
        return reports;
    }

    /** O TCMine confirmou: o relatório sai da fila. */
    void confirm(@NotNull Report report) {
        try {
            Files.deleteIfExists(report.file());
        } catch (IOException e) {
            TcCloud.LOG.warn("Nuvem: não apaguei {} (será reenviado e reconhecido): {}", report.file(), e.toString());
        }
    }
}
