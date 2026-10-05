package org.tinycore.cloud.integration.tcmine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.policy.ItemPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Backend de DESENVOLVIMENTO: a "nuvem" é um arquivo JSON na pasta do jogo/servidor
 * ({@code tccloud-dev-backend.json}), fora dos mundos, então dois mundos do mesmo singleplayer enxergam os
 * mesmos canais (é assim que se testa "sair de um mundo e achar os itens no outro" sem o TCMine).
 *
 * <p>Ligado só com {@code devFileBackend=true} na config. As regras ficam em {@link DevCloudState}; aqui só a
 * thread própria (simula a latência de rede: nada roda na thread do servidor) e a gravação atômica do arquivo
 * depois de cada mudança.
 */
public final class FileCloudBackend implements CloudBackend {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Supplier<Long> ttlMs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tccloud-dev-backend");
        t.setDaemon(true);
        return t;
    });
    private DevCloudState state;
    /** Quem segura os leases: o {@code worldId} do mundo aberto (recebido no hello). */
    private volatile String holder = "?";

    public FileCloudBackend(@NotNull Path file, @NotNull Supplier<Long> ttlMs) {
        this.file = file;
        this.ttlMs = ttlMs;
        this.state = load(file);
    }

    @Override
    public @NotNull CompletableFuture<HelloReply> hello(@NotNull Checkpoint checkpoint) {
        return CompletableFuture.supplyAsync(() -> {
            holder = checkpoint.worldId().toString();
            return new HelloReply(ItemPolicy.OPEN, CloudQuota.UNLIMITED, 0, false, null);
        }, executor);
    }

    @Override
    public @NotNull CompletableFuture<LeaseResult> acquire(@NotNull UUID playerUuid, @NotNull String playerName) {
        return mutate(() -> state.acquire(playerUuid, holder, System.currentTimeMillis(), ttlMs.get()));
    }

    @Override
    public @NotNull CompletableFuture<HeartbeatReply> heartbeat(@NotNull Collection<HeldLease> leases) {
        return mutate(() -> {
            long now = System.currentTimeMillis();
            leases.forEach(l -> state.heartbeat(l.playerUuid(), l.epoch(), holder, now));
            return new HeartbeatReply(List.of(), ItemPolicy.OPEN.version());
        });
    }

    @Override
    public @NotNull CompletableFuture<ItemPolicy> policy() {
        return CompletableFuture.completedFuture(ItemPolicy.OPEN);
    }

    @Override
    public @NotNull CompletableFuture<BatchResult> submit(@NotNull Batch batch, @NotNull List<EncodedItem> definitions) {
        return mutate(() -> state.submit(batch, definitions, holder));
    }

    @Override
    public @NotNull CompletableFuture<Void> release(@NotNull UUID playerUuid, long epoch, long lastSeq) {
        return mutate(() -> {
            state.release(playerUuid, epoch, lastSeq, holder);
            return null;
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> reportDoubtful(@NotNull String reportId,
                                                           @NotNull List<DoubtfulOperation> operations) {
        return mutate(() -> {
            operations.forEach(op -> state.addDoubtful(reportId + ": " + op));
            return null;
        });
    }

    @Override
    public @NotNull CompletableFuture<Void> reportSuspects(@NotNull List<SuspectReport> suspects) {
        suspects.forEach(s -> TcCloud.LOG.info("Nuvem (dev): item suspeito {} ({}), {} tentativa(s).",
                s.itemId(), s.evidence(), s.attempts()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @NotNull String describe() {
        return "arquivo local " + file.toAbsolutePath();
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    /** Roda a mudança na thread do backend e grava o arquivo em seguida. */
    private <T> CompletableFuture<T> mutate(Supplier<T> change) {
        return CompletableFuture.supplyAsync(() -> {
            T result = change.get();
            save();
            return result;
        }, executor);
    }

    private void save() {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Exceção sobe no future: o mod trata como "rede fora do ar" e tenta de novo.
            throw new IllegalStateException("não consegui gravar " + file, e);
        }
    }

    private static DevCloudState load(Path file) {
        if (!Files.exists(file)) return new DevCloudState();
        try {
            DevCloudState loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), DevCloudState.class);
            return loaded != null ? loaded : new DevCloudState();
        } catch (IOException | RuntimeException e) {
            // Guarda o arquivo ruim ao lado em vez de sobrescrevê-lo no próximo save (dá para investigar).
            Path bad = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            TcCloud.LOG.warn("Backend de desenvolvimento ilegível ({}); movido para {} e começando vazio.", file, bad, e);
            try {
                Files.move(file, bad);
            } catch (IOException moveError) {
                TcCloud.LOG.warn("Não consegui mover {}: {}", file, moveError.toString());
            }
            return new DevCloudState();
        }
    }
}
