package org.tinycore.cloud.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
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
import java.util.Objects;
import java.util.UUID;

/**
 * O serviço da nuvem de UM servidor ligado: diário, fila de envio, configuração da nuvem e ciclo de vida (boot,
 * tick, save, logout, parada). Plano §4 e §5. Os jogadores e seus leases ficam em {@link PlayerLeases}; itens
 * (guardar/retirar/listar) em {@link CloudInventory}; os eventos do NeoForge chegam por {@link CloudServerEvents}.
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
    private final PlayerLeases leases;
    private final Map<String, EncodedItem> definitions = new HashMap<>();
    private final CloudListeners listeners = new CloudListeners();
    private final ChannelMounts mounts = new ChannelMounts();
    private final CloudInventory inventory;
    private final CloudReports reports;
    private boolean policyRefreshInFlight;

    private volatile ItemPolicy policy = ItemPolicy.OPEN;
    private CloudQuota quota = CloudQuota.UNLIMITED;
    private int maxItemBytes = Config.MAX_ITEM_BYTES.get();
    private boolean helloDone;
    private boolean helloInFlight;
    private int helloRetryInTicks;
    private boolean stopping;
    /** Diário com problema ou backend mandou: nada entra nem sai em nenhum canal. */
    private @Nullable String globalReadOnly;
    private boolean flushRequested;
    private int ticksSinceFlush = Integer.MAX_VALUE / 2;

    private CloudService(MinecraftServer server, CloudBackend backend, Path folder, Checkpoint checkpoint) {
        this.server = server;
        this.backend = backend;
        this.writer = new JournalWriter(folder, checkpoint, Config.JOURNAL_COMPACT_KB.get() * 1024L);
        this.outbox = new BatchOutbox(backend, server, this::onBatchResult);
        this.leases = new PlayerLeases(new PlayerLeases.Context(backend, server, outbox, listeners, () -> quota,
                () -> helloDone, () -> globalReadOnly, definitions::putAll, this::onHeartbeat));
        this.reports = new CloudReports(backend, server, folder);
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
        Path checkpointFile = folder.resolve("checkpoint.json");
        Checkpoint checkpoint = Checkpoint.read(checkpointFile);
        boolean checkpointLost = checkpoint == null && checkpointFile.toFile().exists();
        CloudService service = new CloudService(server, backend, folder, checkpoint != null ? checkpoint : Checkpoint.fresh());
        instance = service;
        TcCloud.LOG.info("Nuvem ligada: {}.", backend.describe());
        if (checkpointLost) {
            // Sem o checkpoint não dá para detectar "mundo que voltou no tempo": melhor travar que arriscar.
            service.enterGlobalReadOnly("checkpoint ilegível");
        }
        service.replayJournal();
    }

    /** Boot: reporta operações em dúvida, põe na fila os lotes não confirmados e compacta o diário. */
    private void replayJournal() {
        try {
            JournalReplay replay = writer.replay();
            if (replay.uncleanShutdown() && !replay.doubtful().isEmpty()) {
                TcCloud.LOG.warn("Nuvem: o servidor caiu sem parada limpa. Operações em dúvida (o dono decide no painel):");
                replay.doubtful().forEach(op -> TcCloud.LOG.warn("  {}", op));
                // ANTES da compactação, que apaga o que mostrava essas operações. Se gravar falhar, a
                // compactação não roda (a exceção cai no catch) e o próximo boot tenta de novo.
                reports.keepDoubtful(replay.doubtful());
            }
            definitions.putAll(replay.definitions());
            for (Batch batch : replay.outbox()) {
                List<EncodedItem> defs = new ArrayList<>();
                batch.ops().stream().filter(op -> op.delta() > 0)
                        .map(op -> replay.definitions().get(op.key().fingerprint()))
                        .filter(Objects::nonNull).forEach(defs::add);
                outbox.add(new JournalWriter.Outgoing(batch, defs));
            }
            writer.compactNow(replay);
        } catch (IOException e) {
            enterGlobalReadOnly("diário ilegível: " + e);
        }
    }

    /** Resposta do heartbeat: trava canais cujo lease se perdeu e busca a política se ela mudou. */
    private void onHeartbeat(CloudBackend.HeartbeatReply reply) {
        for (UUID lost : reply.lost()) {
            TcCloud.LOG.warn("Nuvem: o lease de {} foi para outro servidor; canal travado aqui.", lost);
            leases.lockReadOnly(lost, "outro servidor pegou os canais");
        }
        if (reply.policyVersion() == policy.version() || policyRefreshInFlight) return;
        policyRefreshInFlight = true;
        backend.policy().whenCompleteAsync((fresh, error) -> {
            policyRefreshInFlight = false;
            if (error != null) {
                TcCloud.LOG.debug("Nuvem: não consegui buscar a política nova: {}", error.toString());
                return;
            }
            policy = fresh;
            TcCloud.LOG.info("Nuvem: política de itens atualizada (versão {}, {} regras).", fresh.version(), fresh.rules().size());
        }, server);
    }

    /** O mod recusou um item por parecer guardar dados no mundo: vai para a fila de suspeitos do dono. */
    void recordSuspect(@NotNull String itemId, @NotNull String evidence, boolean simulate) {
        reports.recordSuspect(itemId, evidence, simulate);
    }

    /** Pede a configuração da nuvem; sem ela nenhum canal é entregue. Tenta de novo enquanto falhar. */
    private void tickHello() {
        if (helloDone || helloInFlight) return;
        if (helloRetryInTicks > 0) {
            helloRetryInTicks--;
            return;
        }
        helloInFlight = true;
        backend.hello(writer.checkpoint()).whenCompleteAsync((reply, error) -> {
            helloInFlight = false;
            if (error != null) {
                TcCloud.LOG.warn("Nuvem: hello falhou ({}); tentando de novo.", error.toString());
                helloRetryInTicks = Config.SEND_RETRY_SECONDS.get() * 20;
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
        leases.releaseAllSettled();
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
        leases.onLogin(player);
    }

    void onLogout(@NotNull ServerPlayer player) {
        if (stopping) return;
        if (leases.onLogout(player)) flushRequested = true;
    }

    // ------------------------------------------------------------------ tick e saves

    void tick() {
        tickHello();
        reports.tick();
        for (PlayerCloud pc : leases.all()) {
            PlayerCloudSession s = pc.session;
            if (s == null) continue;
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
        leases.tick();
    }

    /** Save do overworld (autosave, save-all ou o nosso save com flush). */
    void onWorldSave() {
        for (PlayerCloud pc : leases.all()) {
            if (pc.session != null) persist(pc.session.onWorldSave());
        }
        afterSeal();
    }

    /** Depois de um save com flush (o IO já terminou): todo crédito pendente vira durável. */
    void sealFlushed() {
        for (PlayerCloud pc : leases.all()) {
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
            for (PlayerCloud pc : leases.all()) {
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
        leases.lockReadOnly(batch.playerUuid(), "quarentena");
    }

    private void enterGlobalReadOnly(String reason) {
        if (globalReadOnly == null) TcCloud.LOG.error("Nuvem em SOMENTE LEITURA neste servidor: {}", reason);
        globalReadOnly = reason;
        leases.lockReadOnly(null, reason);
    }

    // ------------------------------------------------------------------ consultas (blocos, tela, AE2)

    /** Situação do jogador (jogador desconhecido = ainda conectando). */
    public @NotNull CloudStatus status(@NotNull UUID player) {
        PlayerCloud pc = leases.get(player);
        return pc == null ? CloudStatus.CONNECTING : pc.status;
    }

    public @Nullable String statusDetail(@NotNull UUID player) {
        PlayerCloud pc = leases.get(player);
        return pc == null ? null : pc.detail;
    }

    /** Sessão ativa do jogador, ou {@code null} (sem lease, ou saindo). */
    public @Nullable PlayerCloudSession session(@NotNull UUID player) {
        PlayerCloud pc = leases.get(player);
        return pc == null || pc.releaseRequested ? null : pc.session;
    }

    /** Canal padrão do jogador (fase 2: o único). */
    public @Nullable UUID defaultChannel(@NotNull UUID player) {
        PlayerCloud pc = leases.get(player);
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
