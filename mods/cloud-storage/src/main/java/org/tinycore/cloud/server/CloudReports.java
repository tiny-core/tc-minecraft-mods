package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.integration.tcmine.CloudBackend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Relatórios para o dono da nuvem, enviados no ritmo do heartbeat: itens suspeitos ({@link SuspectCollector}) e
 * operações em dúvida depois de uma queda ({@link DoubtfulOutbox}). Cada tipo tem a sua trava de "em voo" para
 * não mandar o mesmo duas vezes enquanto a resposta não chega; o que falha fica para a próxima rodada.
 *
 * <p>Só na thread do servidor; respostas voltam por {@code mainThread}.
 */
final class CloudReports {

    private final CloudBackend backend;
    private final Executor mainThread;
    private final DoubtfulOutbox doubtful;
    private final SuspectCollector suspects = new SuspectCollector();
    private final Set<String> doubtfulInFlight = new HashSet<>();
    private boolean suspectsInFlight;
    private int ticksToNext;

    CloudReports(@NotNull CloudBackend backend, @NotNull Executor mainThread, @NotNull Path folder) {
        this.backend = backend;
        this.mainThread = mainThread;
        this.doubtful = new DoubtfulOutbox(folder);
    }

    /** Boot após queda: guarda as operações em dúvida ANTES de o diário ser compactado. */
    void keepDoubtful(@NotNull List<DoubtfulOperation> operations) throws IOException {
        doubtful.add(operations);
    }

    void recordSuspect(@NotNull String itemId, @NotNull String evidence, boolean simulate) {
        suspects.record(itemId, evidence, simulate);
    }

    void tick() {
        if (--ticksToNext > 0) return;
        ticksToNext = Config.HEARTBEAT_SECONDS.get() * 20;
        sendDoubtful();
        sendSuspects();
    }

    private void sendDoubtful() {
        for (DoubtfulOutbox.Report report : doubtful.pending()) {
            if (!doubtfulInFlight.add(report.reportId())) continue;
            backend.reportDoubtful(report.reportId(), report.operations()).whenCompleteAsync((ok, error) -> {
                doubtfulInFlight.remove(report.reportId());
                if (error == null) doubtful.confirm(report);
                else TcCloud.LOG.warn("Nuvem: operações em dúvida não enviadas ({}); tento de novo.", error.toString());
            }, mainThread);
        }
    }

    private void sendSuspects() {
        if (suspectsInFlight) return;
        List<CloudBackend.SuspectReport> batch = suspects.drain();
        if (batch.isEmpty()) return;
        suspectsInFlight = true;
        backend.reportSuspects(batch).whenCompleteAsync((ok, error) -> {
            suspectsInFlight = false;
            if (error != null) suspects.restore(batch);
        }, mainThread);
    }
}
