package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Saldos <b>locais</b> dos canais de um jogador: o que ele vê e pode usar neste servidor (inclui créditos
 * ainda não duráveis). É o cache em memória que o AE2 consulta dezenas de vezes por tick; nada aqui vai à
 * rede nem ao disco.
 *
 * <p>Também aplica a cota do canal ({@link CloudQuota}). Canal desconhecido (não veio no snapshot do
 * lease) não aceita nada: o cliente nunca escolhe um canal que o TCMine não entregou.
 */
public final class ChannelBalances {

    private final Map<UUID, Map<String, Long>> channels = new HashMap<>();
    private final Map<UUID, Long> totals = new HashMap<>();
    private final CloudQuota quota;

    public ChannelBalances(@NotNull Set<UUID> channelIds, @NotNull Map<BalanceKey, Long> snapshot, @NotNull CloudQuota quota) {
        this.quota = quota;
        for (UUID id : channelIds) {
            channels.put(id, new HashMap<>());
            totals.put(id, 0L);
        }
        snapshot.forEach((key, amount) -> {
            if (amount <= 0) return;
            Map<String, Long> items = channels.get(key.channelId());
            if (items == null) throw new IllegalArgumentException("saldo de canal desconhecido: " + key);
            items.put(key.fingerprint(), amount);
            totals.merge(key.channelId(), amount, Long::sum);
        });
    }

    public boolean hasChannel(@NotNull UUID channelId) {
        return channels.containsKey(channelId);
    }

    public @NotNull Set<UUID> channelIds() {
        return Collections.unmodifiableSet(new HashSet<>(channels.keySet()));
    }

    public long get(@NotNull BalanceKey key) {
        Map<String, Long> items = channels.get(key.channelId());
        return items == null ? 0 : items.getOrDefault(key.fingerprint(), 0L);
    }

    /** Itens de um canal (impressão digital → quantidade), só leitura. */
    public @NotNull Map<String, Long> items(@NotNull UUID channelId) {
        Map<String, Long> items = channels.get(channelId);
        return items == null ? Map.of() : Collections.unmodifiableMap(items);
    }

    /** Quanto de {@code amount} pode entrar, respeitando canal existente e cota. Não altera nada. */
    public long insertable(@NotNull BalanceKey key, long amount) {
        Map<String, Long> items = channels.get(key.channelId());
        if (items == null || amount <= 0) return 0;
        boolean isNew = !items.containsKey(key.fingerprint());
        return quota.acceptable(isNew, items.size(), totals.get(key.channelId()), amount);
    }

    /** Quanto de {@code amount} pode sair. Não altera nada. */
    public long extractable(@NotNull BalanceKey key, long amount) {
        if (amount <= 0) return 0;
        return Math.min(amount, get(key));
    }

    /** Aplica uma variação já validada (por {@link #insertable}/{@link #extractable}). */
    void apply(@NotNull BalanceKey key, long delta) {
        Map<String, Long> items = channels.get(key.channelId());
        long after = items.getOrDefault(key.fingerprint(), 0L) + delta;
        if (after < 0) throw new IllegalStateException("saldo local negativo em " + key);
        if (after == 0) items.remove(key.fingerprint());
        else items.put(key.fingerprint(), after);
        totals.merge(key.channelId(), delta, Long::sum);
    }
}
