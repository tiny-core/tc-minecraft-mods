package org.tinycore.cloud.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.block.CloudLinkBlockEntity;
import org.tinycore.cloud.cloud.ChannelBalances;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.PlayerCloudSession;
import org.tinycore.cloud.item.TransferRejection;
import org.tinycore.cloud.network.LinkSyncPayload;
import org.tinycore.cloud.server.ChannelFeedback;
import org.tinycore.cloud.server.CloudEntry;
import org.tinycore.cloud.server.CloudService;
import org.tinycore.cloud.server.CloudStatus;
import org.tinycore.core.grid.CountDiff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servidor: manda à tela aberta do TC Cloud Link só o que mudou (itens pelo {@link CountDiff} do core, cabeçalho
 * por igualdade). Confere a cada {@link #CHECK_TICKS} ticks e só refaz a lista quando o canal mudou
 * ({@code changeCount} da sessão) ou a situação mudou.
 */
final class CloudLinkSync {

    private static final int CHECK_TICKS = 5;
    private static final int FEEDBACK_TICKS = 200;

    private final ServerPlayer player;
    private final int containerId;
    private final CloudLinkBlockEntity link;
    private Map<String, Long> lastSent = new HashMap<>();
    private final Map<String, LinkEntry> current = new HashMap<>();
    private @Nullable LinkHeader lastHeader;
    private @Nullable PlayerCloudSession lastSession;
    private long lastChange = -1;
    private @Nullable UUID lastChannel;
    private int ticks;
    private boolean first = true;
    private @Nullable TransferRejection rejection;
    private ChannelFeedback feedback = ChannelFeedback.NONE;
    /** Tempo de jogo em que o {@link #feedback} expira (some da tela). */
    private long feedbackUntil;

    CloudLinkSync(@NotNull ServerPlayer player, int containerId, @NotNull CloudLinkBlockEntity link) {
        this.player = player;
        this.containerId = containerId;
        this.link = link;
    }

    /** Resultado de criar/renomear canal; fica na tela por {@link #FEEDBACK_TICKS}. */
    void setFeedback(@NotNull ChannelFeedback feedback) {
        this.feedback = feedback;
        this.feedbackUntil = player.level().getGameTime() + FEEDBACK_TICKS;
        soon();
    }

    /** Recusa do último depósito (vai no cabeçalho); {@code null} limpa. */
    void setRejection(@Nullable TransferRejection rejection) {
        this.rejection = rejection;
        ticks = CHECK_TICKS; // mostra já
    }

    /** Pede uma conferência no próximo tick (depois de uma ação do jogador). */
    void soon() {
        ticks = CHECK_TICKS;
    }

    void tick() {
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        CloudService service = CloudService.get();
        UUID owner = player.getUUID();
        PlayerCloudSession session = service == null ? null : service.session(owner);
        LinkHeader header = header(service, owner, session);
        UUID shown = service == null ? null : service.channelFor(owner, link.channel());
        boolean itemsChanged = session != lastSession || !Objects.equals(shown, lastChannel)
                || (session != null && session.changeCount() != lastChange);
        if (!itemsChanged && header.equals(lastHeader) && !first) return;

        List<LinkEntry> changes = new ArrayList<>();
        if (itemsChanged) {
            Map<String, Long> amounts = new HashMap<>();
            current.clear();
            UUID channel = service == null ? null : service.channelFor(owner, link.channel());
            if (session != null && channel != null) {
                int limit = Config.MAX_SYNC_ENTRIES.get();
                for (CloudEntry e : service.inventory().entries(owner, channel)) {
                    if (amounts.size() >= limit) break;
                    ItemStack icon = e.prototype() != null ? e.prototype() : ItemStack.EMPTY;
                    current.put(e.fingerprint(), new LinkEntry(e.fingerprint(), icon, LinkHeader.clip(e.itemId()),
                            LinkHeader.clip(e.name()), e.amount(), e.status().ordinal()));
                    // A situação entra na "contagem" para o diff também perceber troca de situação.
                    amounts.put(e.fingerprint(), e.amount() * 8 + e.status().ordinal());
                }
            }
            CountDiff.forEachChange(lastSent, amounts, (fp, value) -> {
                LinkEntry entry = current.get(fp);
                changes.add(entry != null ? entry : new LinkEntry(fp, ItemStack.EMPTY, "", "", 0, 0));
            });
            lastSent = amounts;
            lastSession = session;
            lastChange = session == null ? -1 : session.changeCount();
            lastChannel = shown;
        }
        send(header, changes);
        lastHeader = header;
    }

    private LinkHeader header(@Nullable CloudService service, UUID owner, @Nullable PlayerCloudSession session) {
        List<LinkChannel> channelList = new ArrayList<>();
        int selected = -1;
        if (service != null) {
            UUID shown = service.channelFor(owner, link.channel());
            for (Map.Entry<UUID, String> e : service.channels(owner).entrySet()) {
                if (channelList.size() >= LinkHeader.MAX_CHANNELS) break;
                if (e.getKey().equals(shown)) selected = channelList.size();
                channelList.add(new LinkChannel(e.getKey(), LinkHeader.clip(e.getValue())));
            }
        }
        CloudStatus status = service == null ? CloudStatus.DISABLED : service.status(owner);
        String detailText = service == null ? null : service.statusDetail(owner);
        String detail = detailText == null ? "" : detailText;
        var conflict = link.mountConflict();
        String where = conflict == null ? "" : conflict.pos().toShortString() + " (" + conflict.dimension().location() + ")";
        return new LinkHeader(status.ordinal(), LinkHeader.clip(detail), link.access().ordinal(), link.priority(),
                LinkHeader.clip(where), rejection == null ? -1 : rejection.ordinal(), link.isNetworkActive(),
                quota(service, owner, session, link.channel()), channelList, selected, currentFeedback().ordinal());
    }

    private ChannelFeedback currentFeedback() {
        return player.level().getGameTime() < feedbackUntil ? feedback : ChannelFeedback.NONE;
    }

    /** Uso da cota do canal mostrado (o padrão do jogador); {@link LinkQuota#NONE} sem sessão. */
    private static LinkQuota quota(@Nullable CloudService service, UUID owner, @Nullable PlayerCloudSession session,
                                   @Nullable UUID wanted) {
        UUID channel = service == null ? null : service.channelFor(owner, wanted);
        if (session == null || channel == null) return LinkQuota.NONE;
        ChannelBalances balances = session.balances();
        CloudQuota limits = balances.quota();
        return new LinkQuota(true, balances.typeCount(channel), limits.maxTypes(), balances.total(channel),
                limits.maxTotal());
    }

    private void send(LinkHeader header, List<LinkEntry> changes) {
        boolean reset = first;
        first = false;
        int from = 0;
        do {
            int to = Math.min(changes.size(), from + LinkSyncPayload.MAX_ENTRIES);
            PacketDistributor.sendToPlayer(player,
                    new LinkSyncPayload(containerId, reset && from == 0, header, List.copyOf(changes.subList(from, to))));
            from = to;
        } while (from < changes.size());
    }
}
