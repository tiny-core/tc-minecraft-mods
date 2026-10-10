package org.tinycore.cloud.integration.tcmine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.ChannelNames;
import org.tinycore.cloud.cloud.CloudOp;
import org.tinycore.cloud.item.EncodedItem;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As regras do TCMine, em miniatura, para a nuvem local ({@link LocalCloudBackend}): lease com
 * época e expiração, lote aplicado uma vez só, saldo nunca negativo, saldo esperado conferido e quarentena.
 * Síncrono e sem Minecraft, para ser testado em JUnit; o {@code LocalCloudBackend} só cuida de thread e arquivo.
 *
 * <p>Os campos públicos sem getters são o formato do JSON salvo (o Gson lê e grava campo a campo).
 */
public final class LocalCloudState {

    public Map<String, PlayerState> players = new HashMap<>();
    public Map<String, ItemState> items = new HashMap<>();
    public List<String> quarantine = new ArrayList<>();
    public List<String> doubtful = new ArrayList<>();

    public static final class PlayerState {
        public String holder;
        public long epoch;
        public long lastSeq;
        public long heartbeatAt;
        public List<ChannelState> channels = new ArrayList<>();
    }

    public static final class ChannelState {
        public String id;
        public String name;
        public Map<String, Long> amounts = new LinkedHashMap<>();
    }

    public static final class ItemState {
        public String itemId;
        public String name;
        public String bytes;

        EncodedItem toEncoded(String fingerprint) {
            return new EncodedItem(fingerprint, itemId, name, Base64.getDecoder().decode(bytes));
        }
    }

    /** Nome do canal criado sozinho no primeiro acesso (fase 2 tem um canal por jogador). */
    public static final String DEFAULT_CHANNEL = "Principal";

    /**
     * @param holder quem pede (o {@code worldId} do mundo, na nuvem local)
     * @param ttlMs  lease sem heartbeat por mais que isso pode ser tomado por outro
     */
    public synchronized @NotNull CloudBackend.LeaseResult acquire(@NotNull UUID player, @NotNull String holder,
                                                                  long now, long ttlMs) {
        PlayerState p = players.computeIfAbsent(player.toString(), k -> new PlayerState());
        boolean heldByOther = p.holder != null && !p.holder.equals(holder) && now - p.heartbeatAt < ttlMs;
        if (heldByOther) return new CloudBackend.LeaseResult.Busy(p.holder);
        if (p.channels.isEmpty()) {
            ChannelState c = new ChannelState();
            c.id = UUID.randomUUID().toString();
            c.name = DEFAULT_CHANNEL;
            p.channels.add(c);
        }
        p.holder = holder;
        p.epoch++;
        p.lastSeq = 0;
        p.heartbeatAt = now;
        return new CloudBackend.LeaseResult.Granted(p.epoch, snapshots(p), false);
    }

    /**
     * Cria um canal (como o {@code POST /channels} do TCMine): só quem segura o lease, nome limpo, sem repetir e até
     * {@code maxChannels} canais.
     */
    public synchronized @NotNull CloudBackend.ChannelResult createChannel(@NotNull UUID player, @NotNull String rawName,
                                                                         @NotNull String holder, int maxChannels) {
        PlayerState p = players.get(player.toString());
        if (p == null || !holder.equals(p.holder)) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.NO_LEASE);
        String name = ChannelNames.clean(rawName);
        if (name == null) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.INVALID_NAME);
        if (ChannelNames.taken(names(p, null), name)) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.DUPLICATE);
        if (p.channels.size() >= maxChannels) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.LIMIT);
        ChannelState c = new ChannelState();
        c.id = UUID.randomUUID().toString();
        c.name = name;
        p.channels.add(c);
        return CloudBackend.ChannelResult.done(UUID.fromString(c.id), name);
    }

    /** Renomeia um canal (como o {@code POST /channels/rename} do TCMine). */
    public synchronized @NotNull CloudBackend.ChannelResult renameChannel(@NotNull UUID player, @NotNull UUID channel,
                                                                         @NotNull String rawName, @NotNull String holder) {
        PlayerState p = players.get(player.toString());
        if (p == null || !holder.equals(p.holder)) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.NO_LEASE);
        ChannelState c = channel(p, channel);
        if (c == null) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.UNKNOWN_CHANNEL);
        String name = ChannelNames.clean(rawName);
        if (name == null) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.INVALID_NAME);
        if (ChannelNames.taken(names(p, c), name)) return CloudBackend.ChannelResult.refused(CloudBackend.ChannelRefusal.DUPLICATE);
        c.name = name;
        return CloudBackend.ChannelResult.done(channel, name);
    }

    /** Nomes dos canais do jogador, menos {@code except}. */
    private static List<String> names(PlayerState p, @Nullable ChannelState except) {
        List<String> names = new ArrayList<>();
        for (ChannelState c : p.channels) {
            if (c != except) names.add(c.name);
        }
        return names;
    }

    public synchronized void heartbeat(@NotNull UUID player, long epoch, @NotNull String holder, long now) {
        PlayerState p = players.get(player.toString());
        if (p != null && holder.equals(p.holder) && p.epoch == epoch) p.heartbeatAt = now;
    }

    public synchronized @NotNull CloudBackend.BatchResult submit(@NotNull Batch batch, @NotNull List<EncodedItem> definitions,
                                                                 @NotNull String holder) {
        for (EncodedItem item : definitions) {
            items.computeIfAbsent(item.fingerprint(), fp -> {
                ItemState s = new ItemState();
                s.itemId = item.itemId();
                s.name = item.displayName();
                s.bytes = Base64.getEncoder().encodeToString(item.bytes());
                return s;
            });
        }
        PlayerState p = players.get(batch.playerUuid().toString());
        if (p == null) return quarantine(batch, "jogador sem lease");
        if (batch.epoch() < p.epoch || (batch.epoch() == p.epoch && !holder.equals(p.holder))) {
            return quarantine(batch, "época velha");
        }
        if (batch.epoch() > p.epoch) return quarantine(batch, "época do futuro");
        if (batch.seq() <= p.lastSeq) return CloudBackend.BatchResult.DUPLICATE;
        if (batch.seq() != p.lastSeq + 1) return quarantine(batch, "buraco na sequência");

        // Confere tudo antes de aplicar qualquer coisa: o lote é atômico.
        Map<BalanceKey, Long> after = new HashMap<>();
        for (CloudOp op : batch.ops()) {
            ChannelState c = channel(p, op.key().channelId());
            if (c == null) return quarantine(batch, "canal desconhecido");
            if (!items.containsKey(op.key().fingerprint())) return quarantine(batch, "item sem definição");
            long value = after.getOrDefault(op.key(), c.amounts.getOrDefault(op.key().fingerprint(), 0L)) + op.delta();
            if (value < 0) return quarantine(batch, "saldo negativo");
            after.put(op.key(), value);
        }
        for (Map.Entry<BalanceKey, Long> e : batch.expected().entrySet()) {
            if (!e.getValue().equals(after.get(e.getKey()))) return quarantine(batch, "divergência de saldo");
        }
        after.forEach((key, value) -> {
            ChannelState c = channel(p, key.channelId());
            if (value == 0) c.amounts.remove(key.fingerprint());
            else c.amounts.put(key.fingerprint(), value);
        });
        p.lastSeq = batch.seq();
        return CloudBackend.BatchResult.APPLIED;
    }

    public synchronized void release(@NotNull UUID player, long epoch, long lastSeq, @NotNull String holder) {
        PlayerState p = players.get(player.toString());
        if (p != null && holder.equals(p.holder) && p.epoch == epoch && p.lastSeq == lastSeq) p.holder = null;
    }

    public synchronized void addDoubtful(@NotNull String line) {
        doubtful.add(line);
    }

    private CloudBackend.BatchResult quarantine(Batch batch, String reason) {
        quarantine.add(reason + ": " + batch);
        return CloudBackend.BatchResult.QUARANTINED;
    }

    private static ChannelState channel(PlayerState p, UUID id) {
        for (ChannelState c : p.channels) {
            if (c.id.equals(id.toString())) return c;
        }
        return null;
    }

    private List<CloudBackend.ChannelSnapshot> snapshots(PlayerState p) {
        List<CloudBackend.ChannelSnapshot> result = new ArrayList<>();
        for (ChannelState c : p.channels) {
            Map<String, EncodedItem> defs = new HashMap<>();
            for (String fp : c.amounts.keySet()) {
                ItemState s = items.get(fp);
                if (s != null) defs.put(fp, s.toEncoded(fp));
            }
            result.add(new CloudBackend.ChannelSnapshot(UUID.fromString(c.id), c.name, Map.copyOf(c.amounts), defs));
        }
        return result;
    }
}
