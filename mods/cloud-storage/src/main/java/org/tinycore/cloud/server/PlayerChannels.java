package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.ChannelNames;
import org.tinycore.cloud.integration.tcmine.CloudBackend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Criar e renomear canais de um jogador, pela tela do TC Cloud Link. Separado do {@link CloudService} (uma
 * responsabilidade por classe): aqui só "canais".
 * <p>
 * Confere tudo antes de chamar a nuvem (lease, nome limpo, sem repetir, limite), e a nuvem confere de novo. Quando
 * ela confirma, o canal entra na sessão viva ({@code ChannelBalances.addChannel}) e já pode receber itens, sem
 * relogar: os lotes daquele canal só existem depois que a nuvem o conhece. Um pedido por jogador por vez.
 * <p>
 * Só na thread do servidor; as respostas voltam por {@code mainThread}.
 */
final class PlayerChannels {

    private final CloudBackend backend;
    private final Executor mainThread;
    private final Function<UUID, PlayerCloud> players;
    private final CloudListeners listeners;
    private final IntSupplier maxChannels;
    private final Set<UUID> inFlight = new HashSet<>();

    PlayerChannels(@NotNull CloudBackend backend, @NotNull Executor mainThread,
                   @NotNull Function<UUID, PlayerCloud> players, @NotNull CloudListeners listeners,
                   @NotNull IntSupplier maxChannels) {
        this.backend = backend;
        this.mainThread = mainThread;
        this.players = players;
        this.listeners = listeners;
        this.maxChannels = maxChannels;
    }

    void create(@NotNull UUID player, @NotNull String rawName, @NotNull Consumer<ChannelFeedback> done) {
        PlayerCloud pc = ready(player, done);
        if (pc == null) return;
        String name = ChannelNames.clean(rawName);
        if (name == null) { done.accept(ChannelFeedback.INVALID_NAME); return; }
        if (ChannelNames.taken(pc.channels.values(), name)) { done.accept(ChannelFeedback.DUPLICATE); return; }
        if (pc.channels.size() >= maxChannels.getAsInt()) { done.accept(ChannelFeedback.LIMIT); return; }
        send(pc, backend.createChannel(player, name), ChannelFeedback.CREATED, done);
    }

    void rename(@NotNull UUID player, @NotNull UUID channel, @NotNull String rawName,
                @NotNull Consumer<ChannelFeedback> done) {
        PlayerCloud pc = ready(player, done);
        if (pc == null) return;
        if (!pc.channels.containsKey(channel)) { done.accept(ChannelFeedback.FAILED); return; }
        String name = ChannelNames.clean(rawName);
        if (name == null) { done.accept(ChannelFeedback.INVALID_NAME); return; }
        List<String> others = new ArrayList<>();
        pc.channels.forEach((id, n) -> { if (!id.equals(channel)) others.add(n); });
        if (ChannelNames.taken(others, name)) { done.accept(ChannelFeedback.DUPLICATE); return; }
        send(pc, backend.renameChannel(player, channel, name), ChannelFeedback.RENAMED, done);
    }

    /** O jogador tem sessão aberta e nenhum pedido em andamento? Senão avisa e devolve null. */
    private @Nullable PlayerCloud ready(UUID player, Consumer<ChannelFeedback> done) {
        PlayerCloud pc = players.apply(player);
        if (pc == null || pc.session == null || pc.releaseRequested) {
            done.accept(ChannelFeedback.NO_LEASE);
            return null;
        }
        if (!inFlight.add(player)) {
            done.accept(ChannelFeedback.WORKING);
            return null;
        }
        done.accept(ChannelFeedback.WORKING);
        return pc;
    }

    private void send(PlayerCloud pc, CompletableFuture<CloudBackend.ChannelResult> call,
                      ChannelFeedback success, Consumer<ChannelFeedback> done) {
        call.whenCompleteAsync((result, error) -> {
            inFlight.remove(pc.uuid);
            if (error != null) {
                TcCloud.LOG.debug("Nuvem: criar/renomear canal de {} falhou: {}", pc.uuid, error.toString());
                done.accept(ChannelFeedback.FAILED);
                return;
            }
            if (!result.ok() || result.id() == null) {
                done.accept(result.refusal() == null ? ChannelFeedback.FAILED : ChannelFeedback.of(result.refusal()));
                return;
            }
            if (players.apply(pc.uuid) != pc) return; // saiu e voltou: o próximo acquire traz o canal
            pc.channels.put(result.id(), result.name());
            if (pc.session != null) pc.session.balances().addChannel(result.id());
            listeners.fire(pc.uuid);
            done.accept(success);
        }, mainThread);
    }
}
