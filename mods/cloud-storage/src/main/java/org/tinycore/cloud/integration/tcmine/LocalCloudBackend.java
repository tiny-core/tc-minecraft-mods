package org.tinycore.cloud.integration.tcmine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.policy.ItemPolicy;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * <b>Nuvem local</b>: a nuvem de quem não usa o TCMine (singleplayer, servidor próprio). Os canais ficam num arquivo
 * JSON deste computador ({@code localCloudFile}), fora dos mundos: todos os mundos que usam o arquivo enxergam os
 * mesmos canais, e um caminho absoluto leva a mesma nuvem para várias instâncias e modpacks.
 * <p>
 * As regras (lease, lote único, saldo nunca negativo, quarentena, canais) ficam em {@link LocalCloudState}; aqui só
 * a thread própria (nada roda na thread do servidor) e a gravação atômica do arquivo depois de cada mudança.
 * <p>
 * <b>Uma instância por vez:</b> o estado fica em memória e o arquivo é reescrito inteiro a cada mudança; duas
 * instâncias abertas no mesmo arquivo apagariam as mudanças uma da outra (perda ou duplicação de itens). Por isso o
 * backend segura uma trava de sistema ({@code <arquivo>.lock}) enquanto existe; quem não consegue a trava não abre
 * ({@link InUseException}).
 */
public final class LocalCloudBackend implements CloudBackend {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Supplier<Long> ttlMs;
    private final Supplier<CloudQuota> quota;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tccloud-local");
        t.setDaemon(true);
        return t;
    });
    private LocalCloudState state;
    private final FileChannel lockChannel;
    private final FileLock lock;
    /** Quem segura os leases: o {@code worldId} do mundo aberto (recebido no hello). */
    private volatile String holder = "?";

    public LocalCloudBackend(@NotNull Path file, @NotNull Supplier<Long> ttlMs) throws InUseException {
        this(file, ttlMs, () -> CloudQuota.UNLIMITED);
    }

    /**
     * @param quota cota entregue no {@code hello} (config {@code localQuota*})
     * @throws InUseException outra instância (ou outro mundo aberto neste jogo) está com o arquivo
     */
    public LocalCloudBackend(@NotNull Path file, @NotNull Supplier<Long> ttlMs, @NotNull Supplier<CloudQuota> quota)
            throws InUseException {
        this.file = file;
        this.ttlMs = ttlMs;
        this.quota = quota;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            this.lockChannel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new InUseException("não consegui abrir a trava de " + file + ": " + e);
        }
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (IOException | OverlappingFileLockException e) {
            acquired = null; // OverlappingFileLockException = este mesmo jogo já está com a trava
        }
        if (acquired == null) {
            closeQuietly(lockChannel);
            throw new InUseException("a nuvem local " + file + " está aberta em outra instância do jogo");
        }
        this.lock = acquired;
        this.state = load(file);
    }

    /** A nuvem local não pôde ser aberta (arquivo em uso ou sem acesso). */
    public static final class InUseException extends Exception {
        public InUseException(String message) {
            super(message);
        }
    }

    @Override
    public @NotNull CompletableFuture<HelloReply> hello(@NotNull Checkpoint checkpoint) {
        return CompletableFuture.supplyAsync(() -> {
            holder = checkpoint.worldId().toString();
            return new HelloReply(ItemPolicy.OPEN, quota.get(), 0, false, null, 0);
        }, executor);
    }

    @Override
    public @NotNull CompletableFuture<LeaseResult> acquire(@NotNull UUID playerUuid, @NotNull String playerName) {
        return mutate(() -> state.acquire(playerUuid, holder, System.currentTimeMillis(), ttlMs.get()));
    }

    @Override
    public @NotNull CompletableFuture<ChannelResult> createChannel(@NotNull UUID playerUuid, @NotNull String name) {
        return mutate(() -> state.createChannel(playerUuid, name, holder, Config.MAX_CHANNELS.get()));
    }

    @Override
    public @NotNull CompletableFuture<ChannelResult> renameChannel(@NotNull UUID playerUuid, @NotNull UUID channelId,
                                                                  @NotNull String name) {
        return mutate(() -> state.renameChannel(playerUuid, channelId, name, holder));
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
        suspects.forEach(s -> TcCloud.LOG.info("Nuvem local: item suspeito {} ({}), {} tentativa(s).",
                s.itemId(), s.evidence(), s.attempts()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @NotNull String describe() {
        return "nuvem local em " + file.toAbsolutePath();
    }

    /**
     * Fecha: deixa terminar as gravações na fila e só então solta a trava (outra instância pode abrir o arquivo).
     */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                TcCloud.LOG.warn("Nuvem local: gravações ainda em andamento ao fechar {}.", file);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            lock.release();
        } catch (IOException e) {
            TcCloud.LOG.debug("Nuvem local: soltar a trava falhou: {}", e.toString());
        }
        closeQuietly(lockChannel);
    }

    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            TcCloud.LOG.debug("Nuvem local: fechar a trava falhou: {}", e.toString());
        }
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

    private static LocalCloudState load(Path file) {
        if (!Files.exists(file)) return new LocalCloudState();
        try {
            LocalCloudState loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LocalCloudState.class);
            return loaded != null ? loaded : new LocalCloudState();
        } catch (IOException | RuntimeException e) {
            // Guarda o arquivo ruim ao lado em vez de sobrescrevê-lo no próximo save (dá para investigar).
            Path bad = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            TcCloud.LOG.warn("Nuvem local ilegível ({}); movida para {} e começando vazia.", file, bad, e);
            try {
                Files.move(file, bad);
            } catch (IOException moveError) {
                TcCloud.LOG.warn("Não consegui mover {}: {}", file, moveError.toString());
            }
            return new LocalCloudState();
        }
    }
}
