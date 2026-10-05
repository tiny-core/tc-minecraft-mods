package org.tinycore.cloud.server;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.PlayerCloudSession;
import org.tinycore.cloud.integration.tcmine.CloudBackend;
import org.tinycore.cloud.item.EncodedItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Os jogadores deste servidor e os leases deles (plano §4): pedir o canal no login, abrir a sessão quando o
 * backend entrega, renovar com heartbeat e liberar depois do logout, quando tudo estiver durável e confirmado.
 * Separado do {@link CloudService} para cada classe ter uma responsabilidade: aqui, "quem segura o quê".
 *
 * <p>Só na thread do servidor; respostas do backend voltam por {@code mainThread}.
 */
final class PlayerLeases {

    /** O que a classe precisa do serviço, sem conhecer o serviço inteiro. */
    record Context(@NotNull CloudBackend backend, @NotNull Executor mainThread, @NotNull BatchOutbox outbox,
                   @NotNull CloudListeners listeners, @NotNull Supplier<CloudQuota> quota,
                   @NotNull Supplier<Boolean> ready, @NotNull Supplier<String> globalReadOnly,
                   @NotNull Consumer<Map<String, EncodedItem>> learnItems,
                   @NotNull Consumer<CloudBackend.HeartbeatReply> onHeartbeat) {}

    private final Context ctx;
    private final Map<UUID, PlayerCloud> players = new HashMap<>();
    private int ticksToHeartbeat;

    PlayerLeases(@NotNull Context ctx) {
        this.ctx = ctx;
    }

    void onLogin(@NotNull ServerPlayer player) {
        PlayerCloud pc = players.get(player.getUUID());
        if (pc != null) {
            pc.releaseRequested = false; // voltou antes de liberar: continua com a mesma sessão
            return;
        }
        players.put(player.getUUID(), new PlayerCloud(player.getUUID(), player.getGameProfile().getName()));
        ctx.listeners().fire(player.getUUID());
    }

    /** @return true se o jogador tinha nuvem aqui (o serviço então agenda o save com flush) */
    boolean onLogout(@NotNull ServerPlayer player) {
        PlayerCloud pc = players.get(player.getUUID());
        if (pc == null) return false;
        pc.releaseRequested = true;
        ctx.listeners().fire(pc.uuid);
        return true;
    }

    /** Um passo por tick: pede canais pendentes, libera os que terminaram e manda o heartbeat. */
    void tick() {
        for (PlayerCloud pc : players.values()) {
            if (pc.session == null && !pc.releaseRequested) tryAcquire(pc);
        }
        releaseFinished();
        heartbeat();
    }

    private void tryAcquire(PlayerCloud pc) {
        // Lotes antigos do jogador (diário do boot) saem antes: um acquire novo aumenta a época e os mandaria
        // para a quarentena.
        if (pc.acquireInFlight || !ctx.ready().get() || ctx.outbox().hasPendingFor(pc.uuid)) return;
        if (pc.retryInTicks > 0) {
            pc.retryInTicks--;
            return;
        }
        pc.acquireInFlight = true;
        ctx.backend().acquire(pc.uuid, pc.name).whenCompleteAsync((result, error) -> {
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
        }, ctx.mainThread());
    }

    private void openSession(PlayerCloud pc, CloudBackend.LeaseResult.Granted granted) {
        Map<BalanceKey, Long> snapshot = new HashMap<>();
        pc.channels.clear();
        for (CloudBackend.ChannelSnapshot channel : granted.channels()) {
            pc.channels.put(channel.id(), channel.name());
            channel.amounts().forEach((fp, amount) -> snapshot.put(new BalanceKey(channel.id(), fp), amount));
            ctx.learnItems().accept(channel.items());
        }
        pc.session = new PlayerCloudSession(pc.uuid, granted.epoch(), 0, pc.channels.keySet(), snapshot, ctx.quota().get());
        String globalReason = ctx.globalReadOnly().get();
        boolean readOnly = granted.readOnly() || globalReason != null;
        pc.session.setReadOnly(readOnly);
        setStatus(pc, readOnly ? CloudStatus.READ_ONLY : CloudStatus.ACTIVE, globalReason);
    }

    private void releaseFinished() {
        players.values().removeIf(pc -> {
            if (!pc.releaseRequested || pc.acquireInFlight) return false;
            PlayerCloudSession s = pc.session;
            if (s == null) return true; // nunca teve lease
            if (!s.isSettled() || ctx.outbox().hasPendingFor(pc.uuid)) return false;
            ctx.backend().release(pc.uuid, s.epoch(), s.lastSeq()).exceptionally(e -> {
                TcCloud.LOG.debug("Nuvem: release de {} falhou ({}); o lease expira sozinho.", pc.uuid, e.toString());
                return null;
            });
            ctx.listeners().fire(pc.uuid);
            return true;
        });
    }

    /** Parada do servidor: libera, sem esperar, os leases que já estão em dia. */
    void releaseAllSettled() {
        for (PlayerCloud pc : players.values()) {
            PlayerCloudSession s = pc.session;
            if (s != null && s.isSettled() && !ctx.outbox().hasPendingFor(pc.uuid)) {
                ctx.backend().release(pc.uuid, s.epoch(), s.lastSeq());
            }
        }
    }

    private void heartbeat() {
        if (--ticksToHeartbeat > 0) return;
        ticksToHeartbeat = Config.HEARTBEAT_SECONDS.get() * 20;
        List<CloudBackend.HeldLease> held = new ArrayList<>();
        players.values().forEach(pc -> {
            if (pc.session != null) held.add(new CloudBackend.HeldLease(pc.uuid, pc.session.epoch()));
        });
        if (held.isEmpty()) return;
        ctx.backend().heartbeat(held).whenCompleteAsync((reply, error) -> {
            if (error != null) {
                TcCloud.LOG.debug("Nuvem: heartbeat falhou: {}", error.toString());
                return;
            }
            ctx.onHeartbeat().accept(reply);
        }, ctx.mainThread());
    }

    /** Trava um jogador (lote em quarentena) ou todos ({@code player == null}: diário com problema). */
    void lockReadOnly(@Nullable UUID player, @NotNull String reason) {
        for (PlayerCloud pc : players.values()) {
            if (pc.session == null || (player != null && !player.equals(pc.uuid))) continue;
            pc.session.setReadOnly(true);
            setStatus(pc, CloudStatus.READ_ONLY, reason);
        }
    }

    private void setStatus(PlayerCloud pc, CloudStatus status, @Nullable String detail) {
        pc.status = status;
        pc.detail = detail;
        ctx.listeners().fire(pc.uuid);
    }

    /** Todos os jogadores com nuvem neste servidor (para selar lotes em todos). */
    @NotNull Collection<PlayerCloud> all() {
        return players.values();
    }

    @Nullable PlayerCloud get(@NotNull UUID player) {
        return players.get(player);
    }
}
