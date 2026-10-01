package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.cloud.PlayerCloudSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O que o {@link CloudService} sabe de um jogador neste servidor: situação, sessão (quando o lease chegou),
 * canais e o que falta para liberar o lease depois do logout. Só usado na thread do servidor.
 */
final class PlayerCloud {

    final UUID uuid;
    final String name;
    CloudStatus status = CloudStatus.CONNECTING;
    /** Complemento da situação (ex.: quem está com o canal). */
    @Nullable String detail;
    @Nullable PlayerCloudSession session;
    /** Canais do jogador: id → nome, na ordem do backend. */
    final Map<UUID, String> channels = new LinkedHashMap<>();
    boolean acquireInFlight;
    int retryInTicks;
    /** Saiu do servidor: liberar o lease quando tudo estiver durável e confirmado. */
    boolean releaseRequested;
    /** Último {@code changeCount} cujos créditos pendentes já foram para o diário. */
    long pendingWrittenAt = -1;

    PlayerCloud(@NotNull UUID uuid, @NotNull String name) {
        this.uuid = uuid;
        this.name = name;
    }

    @Nullable UUID defaultChannel() {
        return channels.isEmpty() ? null : channels.keySet().iterator().next();
    }
}
