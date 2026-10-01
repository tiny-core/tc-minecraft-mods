package org.tinycore.cloud.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.PlayerCloudSession;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.JournalReplay;
import org.tinycore.cloud.integration.tcmine.CloudBackend;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.policy.ItemPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O serviço da nuvem de UM servidor ligado: sessões dos jogadores, diário, fila de envio e ciclo de vida
 * (boot, login, tick, save, logout, parada). Plano §4 e §5. Itens (guardar/retirar/listar) ficam em
 * {@link CloudInventory}; os eventos do NeoForge chegam por {@link CloudServerEvents}.
 *
 * <p>Tudo aqui roda na thread do servidor. Respostas do backend voltam para ela por {@code server::execute}.
 *
 * <p>Ordem que garante "nunca duplicar" (plano §5): fim do tick → débitos no diário; save do overworld →
 * créditos que esperavam o IO do save anterior; save com flush (logout, parada, comando) → todos os créditos.
 */
public final class CloudService {

    private static @Nullable CloudService instance;

    private final MinecraftServer server;
    private final CloudBackend backend;
    private final JournalWriter writer;
    private final BatchOutbox outbox;
    private final Map<UUID, PlayerCloud> players = new HashMap<>();
    private final Map<String, EncodedItem> definitions = new HashMap<>();
    private final CloudListeners listeners = new CloudListeners();
    private final CloudInventory inventory;
    private final ChannelMounts mounts = new ChannelMounts();

    private volatile ItemPolicy policy = ItemPolicy.OPEN;
    private CloudQuota quota = CloudQuota.UNLIMITED;
    private int maxItemBytes = Config.MAX_ITEM_BYTES.get();
    private boolean helloDone;
    private boolean stopping;
    /** Diário com problema ou backend mandou: nada entra nem sai em nenhum canal. */
    private @Nullable String globalReadOnly;
    private boolean flushRequested;
    private int ticksSinceFlush = Integer.MAX_VALUE / 2;
    private int ticksToHeartbeat;

    private CloudService(MinecraftServer server, CloudBackend backend, Path folder, Checkpoint checkpoint) {
        this.server = server;
        this.backend = backend;
        this.writer = new JournalWriter(folder, checkpoint, Config.JOURNAL_COMPACT_KB.get() * 1024L);
        this.outbox = new BatchOutbox(backend, server, this::onBatchResult);
        this.inventory = new CloudInventory(this, server.registryAccess());
    }

    /** O serviço do servidor atual, ou {@code null} se a nuvem está desligada (sem backend). */
    public static @Nullable CloudService get() {
        return instance;
    }

    // ------------------------------------------------------------------ ciclo de vida

    static void start(@NotNull MinecraftServer server) {
        CloudBackend backend = CloudBackends.create(server);
        if (backend == null) {
            TcCloud.LOG.info("Nuvem desligada neste servidor (sem backend configurado).");
            return;
        }
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("tccloud");
        Checkpoint checkpoint = Checkpoint.read(folder.resolve("checkpoint.json"));
        boolean checkpointLost = checkpoint == null && folder.resolve("checkpoint.json").toFile().exists();
        CloudService service = new CloudService(server, backend, folder, checkpoint != null ? checkpoint : Checkpoint.fresh());
        instance = service;
        TcCloud.LOG.info("Nuvem ligada: {}.", backend.describe());
        if (checkpointLost) {
            // Sem o checkpoint não dá para detectar "mundo que voltou no tempo": melhor travar que arriscar.
            service.enterGlobalReadOnly("checkpoint ilegível");
        }
        service.boot();
    }

    private void boot() {
        try {
            JournalReplay replay = writer.replay();
            if (replay.uncleanShutdown() && !replay.doubtful().isEmpty()) {
                TcCloud.LOG.warn("Nuvem: o servidor caiu sem parada limpa. Operações em dúvida (o dono decide):");
                replay.doubtful().forEach(op -> TcCloud.LOG.warn("  {}", op));
                backend.reportDoubtful(replay.doubtful()).exceptionally(e -> {
                    TcCloud.LOG.warn("Nuvem: não consegui reportar as operações em dúvida: {}", e.toString());
                    return null;
                });
            }
            definitions.putAll(replay.definitions());
            for (Batch batch : replay.outbox()) {
                List<EncodedItem> defs = new ArrayList<>();
                batch.ops().stream().filter(op -> op.delta() > 0)
                        .map(op -> replay.definitions().get(op.key().fingerprint()))
                        .filter(java.util.Objects::nonNull).forEach(defs::add);
                outbox.add(new JournalWriter.Outgoing(batch, defs));
            }
            writer.compactNow(replay);
        } catch (IOException e) {
            enterGlobalReadOnly("diário ilegível: " + e);
        }
        backend.hello(writer.checkpoint()).whenCompleteAsync((reply, error) -> {
            if (error != null) {
                TcCloud.LOG.warn("Nuvem: hello falhou ({}); tentando no próximo login.", error.toString());
                return;
            }
            policy = reply.policy();
            quota = reply.quota();
            if (reply.maxItemBytes() > 0) maxItemBytes = reply.maxItemBytes();
            if (reply.readOnly()) enterGlobalReadOnly(reply.readOnlyReason() != null ? reply.readOnlyReason() : "backend");
            helloDone = true;
        }, server);
    }

    /** Começou a parada do servidor: logouts a partir daqui não disparam save (a parada salva tudo). */
    void markStopping() {
        stopping = true;
    }

    /** O overworld foi descarregado na parada: o mundo inteiro já está salvo com flush. */
    void onShutdownSaved() {
        sealFlushed();
        outbox.drainBlocking(5000);
        for (PlayerCloud pc : players.values()) {
            PlayerCloudSession s = pc.session;
            if (s != null && s.isSettled() && !outbox.hasPendingFor(pc.uuid)) {
                backend.release(pc.uuid, s.epoch(), s.lastSeq());
            }
        }
        try {
            writer.writeCleanShutdown();
        } catch (IOException e) {
            TcCloud.LOG.error("Nuvem: não consegui marcar a parada limpa; o próximo boot tratará como queda.", e);
        }
    }

    static void stop() {
        CloudService service = instance;
        instance = null;
        if (service != null) service.backend.close();
    }

    // ------------------------------------------------------------------ jogadores

    void onLogin(@NotNull ServerPlayer player) {
        PlayerCloud pc = players.get(player.getUUID());
        if (pc != null) {
            pc.releaseRequested = false; // voltou antes de liberar: continua com a mesma sessão
            return;
        }
        players.put(player.getUUID(), new PlayerCloud(player.getUUID(), player.getGameProfile().getName()));
        listeners.fire(player.getUUID());
    }

    void onLogout(@NotNull ServerPlayer player) {
        PlayerCloud pc = players.get(player.getUUID());
        if (pc == null || stopping) return;
        pc.releaseRequested = true;
        flushRequested = true;
        listeners.fire(pc.uuid);
    }

    private void tryAcquire(PlayerCloud pc) {
        if (pc.acquireInFlight || pc.session != null || !helloDone || outbox.hasPendingFor(pc.uuid)) return;
        if (pc.retryInTicks > 0) {
            pc.retryInTicks--;
            return;
        }
        pc.acquireInFlight = true;
        backend.acquire(pc.uuid, pc.name).whenCompleteAsync((result, error) -> {
            pc.acquireInFlight = false;
            if (players.get(pc.uuid) != pc) return; // saiu e a entrada foi trocada
            if (error != null) {
                setStatus(pc, CloudStatus.UNAVAILABLE, null);
                pc.retryInTicks = Config.SEND_RETRY_SECONDS.get() * 20;
                return;
            }
            switch (result) {
                case CloudBackend.LeaseResult.Busy busy -> {
                    setStatus(pc, CloudStatus.BUSY, busy.holder());
                    pc.retryInTicks = Config.SEND_RETRY_SECONDS.get() * 20;
                }
                case CloudBackend.LeaseResult.Granted granted -> openSession(pc, granted);
            }
        }, server);
    }

    private void openSession(PlayerCloud pc, CloudBackend.LeaseResult.Granted granted) {
        Map<BalanceKey, Long> snapshot = new HashMap<>();
        pc.channels.clear();
        for (CloudBackend.ChannelSnapshot channel : granted.channels()) {
            pc.channels.put(channel.id(), channel.name());
            channel.amounts().forEach((fp, amount) -> snapshot.put(new BalanceKey(channel.id(), fp), amount));
            definitions.putAll(channel.items());
        }
        pc.session = new PlayerCloudSession(pc.uuid, granted.epoch(), 0, pc.channels.keySet(), snapshot, quota);
        boolean readOnly = granted.readOnly() || globalReadOnly != null;
        pc.session.setReadOnly(readOnly);
        setStatus(pc, readOnly ? CloudStatus.READ_ONLY : CloudStatus.ACTIVE, globalReadOnly);
    }

    // ------------------------------------------------------------------ tick e saves

    void tick() {
        for (PlayerCloud pc : players.values()) {
            PlayerCloudSession s = pc.session;
            if (s == null) {
                if (!pc.releaseRequested) tryAcquire(pc);
                continue;
            }
            persist(s.endOfTick());
            writePendingIfChanged(pc, s);
        }
        outbox.tick(Config.SEND_RETRY_SECONDS.get() * 20);
        ticksSinceFlush++;
        if (flushRequested && ticksSinceFlush >= Config.LOGOUT_SAVE_COOLDOWN_SECONDS.get() * 20) {
            flushRequested = false;
            ticksSinceFlush = 0;
            server.saveEverything(true, true, false); // espera o IO terminar antes de voltar
            sealFlushed();
        }
        releaseFinished();
        heartbeat();
    }

    /** Save do overworld (autosave, save-all ou o nosso save com flush). */
    void onWorldSave() {
        for (PlayerCloud pc : players.values()) {
            if (pc.session != null) persist(pc.session.onWorldSave());
        }
        afterSeal();
    }

    /** Depois de um save com flush (o IO já terminou): todo crédito pendente vira durável. */
    void sealFlushed() {
        for (PlayerCloud pc : players.values()) {
            if (pc.session != null) persist(pc.session.onFlushedSave());
        }
        afterSeal();
    }

    /** {@code /tccloud checkpoint}: save com flush e selagem (o backup a quente do TCMine chama). */
    public void checkpointNow() {
        server.saveEverything(true, true, true);
        sealFlushed();
    }

    private void afterSeal() {
        try {
            for (PlayerCloud pc : players.values()) {
                if (pc.session != null) {
                    writer.writePending(pc.uuid, pc.session.pendingCredits());
                    pc.pendingWrittenAt = pc.session.changeCount();
                }
            }
            writer.writeSaveMark();
        } catch (IOException e) {
            enterGlobalReadOnly("falha ao gravar o diário: " + e);
        }
    }

    private void writePendingIfChanged(PlayerCloud pc, PlayerCloudSession s) {
        if (pc.pendingWrittenAt == s.changeCount()) return;
        try {
            writer.writePending(pc.uuid, s.pendingCredits());
            pc.pendingWrittenAt = s.changeCount();
        } catch (IOException e) {
            enterGlobalReadOnly("falha ao gravar o diário: " + e);
        }
    }

    private void persist(List<Batch> batches) {
        if (batches.isEmpty()) return;
        try {
            writer.writeBatches(batches, definitions::get).forEach(outbox::add);
        } catch (IOException | RuntimeException e) {
            enterGlobalReadOnly("falha ao gravar lote no diário: " + e);
        }
    }

    private void onBatchResult(Batch batch, CloudBackend.BatchResult result) {
        try {
            writer.writeAck(batch);
        } catch (IOException e) {
            TcCloud.LOG.warn("Nuvem: não gravei a confirmação do lote (será reenviado e reconhecido): {}", e.toString());
        }
        if (result != CloudBackend.BatchResult.QUARANTINED) return;
        TcCloud.LOG.warn("Nuvem: lote {}/{} do jogador {} foi para a quarentena; canal travado.",
                batch.epoch(), batch.seq(), batch.playerUuid());
        PlayerCloud pc = players.get(batch.playerUuid());
        if (pc != null && pc.session != null) {
            pc.session.setReadOnly(true);
            setStatus(pc, CloudStatus.READ_ONLY, "quarentena");
        }
    }

    private void releaseFinished() {
        players.values().removeIf(pc -> {
            if (!pc.releaseRequested || pc.acquireInFlight) return false;
            PlayerCloudSession s = pc.session;
            if (s == null) return true; // nunca teve lease
            if (!s.isSettled() || outbox.hasPendingFor(pc.uuid)) return false;
            backend.release(pc.uuid, s.epoch(), s.lastSeq()).exceptionally(e -> {
                TcCloud.LOG.debug("Nuvem: release de {} falhou ({}); o lease expira sozinho.", pc.uuid, e.toString());
                return null;
            });
            listeners.fire(pc.uuid);
            return true;
        });
    }

    private void heartbeat() {
        if (--ticksToHeartbeat > 0) return;
        ticksToHeartbeat = Config.HEARTBEAT_SECONDS.get() * 20;
        List<CloudBackend.HeldLease> held = new ArrayList<>();
        players.values().forEach(pc -> {
            if (pc.session != null) held.add(new CloudBackend.HeldLease(pc.uuid, pc.session.epoch()));
        });
        if (held.isEmpty()) return;
        backend.heartbeat(held).exceptionally(e -> {
            TcCloud.LOG.debug("Nuvem: heartbeat falhou: {}", e.toString());
            return null;
        });
    }

    private void enterGlobalReadOnly(String reason) {
        if (globalReadOnly == null) TcCloud.LOG.error("Nuvem em SOMENTE LEITURA neste servidor: {}", reason);
        globalReadOnly = reason;
        for (PlayerCloud pc : players.values()) {
            if (pc.session != null) {
                pc.session.setReadOnly(true);
                setStatus(pc, CloudStatus.READ_ONLY, reason);
            }
        }
    }

    private void setStatus(PlayerCloud pc, CloudStatus status, @Nullable String detail) {
        pc.status = status;
        pc.detail = detail;
        listeners.fire(pc.uuid);
    }

    // ------------------------------------------------------------------ consultas (blocos, tela, AE2)

    /** Situação do jogador (jogador desconhecido = ainda conectando). */
    public @NotNull CloudStatus status(@NotNull UUID player) {
        PlayerCloud pc = players.get(player);
        return pc == null ? CloudStatus.CONNECTING : pc.status;
    }

    public @Nullable String statusDetail(@NotNull UUID player) {
        PlayerCloud pc = players.get(player);
        return pc == null ? null : pc.detail;
    }

    /** Sessão ativa do jogador, ou {@code null} (sem lease, ou saindo). */
    public @Nullable PlayerCloudSession session(@NotNull UUID player) {
        PlayerCloud pc = players.get(player);
        return pc == null || pc.releaseRequested ? null : pc.session;
    }

    /** Canal padrão do jogador (fase 2: o único). */
    public @Nullable UUID defaultChannel(@NotNull UUID player) {
        PlayerCloud pc = players.get(player);
        return pc == null ? null : pc.defaultChannel();
    }

    public @NotNull CloudInventory inventory() {
        return inventory;
    }

    public @NotNull ChannelMounts mounts() {
        return mounts;
    }

    public @NotNull CloudListeners listeners() {
        return listeners;
    }

    @NotNull ItemPolicy policy() {
        return policy;
    }

    int maxItemBytes() {
        return maxItemBytes;
    }

    @Nullable EncodedItem definition(@NotNull String fingerprint) {
        return definitions.get(fingerprint);
    }

    void remember(@NotNull EncodedItem item) {
        definitions.putIfAbsent(item.fingerprint(), item);
    }
}
