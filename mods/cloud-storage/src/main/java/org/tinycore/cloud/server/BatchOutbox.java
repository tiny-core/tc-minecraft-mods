package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.integration.tcmine.CloudBackend;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Fila de envio dos lotes já gravados no diário. Envia <b>um por vez, na ordem</b>: o backend exige a
 * sequência sem buracos, e um lote enviado fora de ordem iria para a quarentena.
 *
 * <p>Falha de rede: o lote fica na cabeça da fila e é reenviado depois de {@code retryTicks}. Isso nunca
 * duplica: o backend reconhece o reenvio pelo (jogador, época, seq) e responde {@code DUPLICATE}.
 *
 * <p>Só mexida na thread do servidor; o resultado do backend volta para ela pelo {@code mainThread}.
 */
final class BatchOutbox {

    private final CloudBackend backend;
    private final Executor mainThread;
    private final BiConsumer<Batch, CloudBackend.BatchResult> onResult;
    private final Deque<JournalWriter.Outgoing> queue = new ArrayDeque<>();
    private boolean inFlight;
    private int retryInTicks;

    /**
     * @param onResult chamado na thread do servidor quando o backend responde (aplicado, duplicado ou quarentena)
     */
    BatchOutbox(@NotNull CloudBackend backend, @NotNull Executor mainThread,
                @NotNull BiConsumer<Batch, CloudBackend.BatchResult> onResult) {
        this.backend = backend;
        this.mainThread = mainThread;
        this.onResult = onResult;
    }

    void add(@NotNull JournalWriter.Outgoing outgoing) {
        queue.addLast(outgoing);
    }

    /** Chamado a cada tick: manda o próximo se nada estiver em voo e não estiver esperando para tentar de novo. */
    void tick(int retryTicks) {
        if (retryInTicks > 0) {
            retryInTicks--;
            return;
        }
        if (inFlight || queue.isEmpty()) return;
        JournalWriter.Outgoing head = queue.peekFirst();
        inFlight = true;
        backend.submit(head.batch(), head.definitions()).whenCompleteAsync((result, error) -> {
            inFlight = false;
            if (error != null) {
                if (queue.peekFirst() != head) return;
                TcCloud.LOG.warn("Nuvem: envio do lote {}/{} falhou ({}); tentando de novo.",
                        head.batch().epoch(), head.batch().seq(), error.toString());
                retryInTicks = retryTicks;
                return;
            }
            // A parada do servidor pode ter enviado este mesmo lote por drainBlocking: só tira se ainda é a cabeça.
            if (queue.peekFirst() != head) return;
            queue.pollFirst();
            onResult.accept(head.batch(), result);
        }, mainThread);
    }

    /** Ainda há lote deste jogador esperando confirmação? */
    boolean hasPendingFor(@NotNull UUID player) {
        for (JournalWriter.Outgoing o : queue) {
            if (o.batch().playerUuid().equals(player)) return true;
        }
        return false;
    }

    boolean isEmpty() {
        return queue.isEmpty() && !inFlight;
    }

    /**
     * Parada do servidor: tenta esvaziar a fila sem a thread do servidor (ela está parando), com teto de tempo.
     * O que não sair fica no diário e é reenviado no próximo boot.
     */
    void drainBlocking(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            JournalWriter.Outgoing head = queue.peekFirst();
            try {
                CloudBackend.BatchResult result = backend.submit(head.batch(), head.definitions())
                        .get(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                queue.pollFirst();
                onResult.accept(head.batch(), result);
            } catch (Exception e) {
                TcCloud.LOG.warn("Nuvem: {} lote(s) ficam no diário para o próximo boot ({}).", queue.size(), e.toString());
                return;
            }
        }
    }
}
