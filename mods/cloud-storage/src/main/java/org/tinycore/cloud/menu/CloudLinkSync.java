package org.tinycore.cloud.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.block.CloudLinkBlockEntity;
import org.tinycore.cloud.cloud.PlayerCloudSession;
import org.tinycore.cloud.item.TransferRejection;
import org.tinycore.cloud.network.LinkSyncPayload;
import org.tinycore.cloud.server.CloudEntry;
import org.tinycore.cloud.server.CloudService;
import org.tinycore.cloud.server.CloudStatus;
import org.tinycore.core.grid.CountDiff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Servidor: manda à tela aberta do TC Cloud Link só o que mudou (itens pelo {@link CountDiff} do core, cabeçalho
 * por igualdade). Confere a cada {@link #CHECK_TICKS} ticks e só refaz a lista quando o canal mudou
 * ({@code changeCount} da sessão) ou a situação mudou.
 */
final class CloudLinkSync {

    private static final int CHECK_TICKS = 5;

    private final ServerPlayer player;
    private final int containerId;
    private final CloudLinkBlockEntity link;
    private Map<String, Long> lastSent = new HashMap<>();
    private final Map<String, LinkEntry> current = new HashMap<>();
    private @Nullable LinkHeader lastHeader;
    private @Nullable PlayerCloudSession lastSession;
    private long lastChange = -1;
    private int ticks;
    private boolean first = true;
    private @Nullable TransferRejection rejection;

    CloudLinkSync(@NotNull ServerPlayer player, int containerId, @NotNull CloudLinkBlockEntity link) {
        this.player = player;
        this.containerId = containerId;
        this.link = link;
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
        LinkHeader header = header(service, owner);
        boolean itemsChanged = session != lastSession || (session != null && session.changeCount() != lastChange);
        if (!itemsChanged && header.equals(lastHeader) && !first) return;

        List<LinkEntry> changes = new ArrayList<>();
        if (itemsChanged) {
            Map<String, Long> amounts = new HashMap<>();
            current.clear();
            UUID channel = service == null ? null : service.defaultChannel(owner);
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
        }
        send(header, changes);
        lastHeader = header;
    }

    private LinkHeader header(@Nullable CloudService service, UUID owner) {
        CloudStatus status = service == null ? CloudStatus.DISABLED : service.status(owner);
        String detailText = service == null ? null : service.statusDetail(owner);
        String detail = detailText == null ? "" : detailText;
        var conflict = link.mountConflict();
        String where = conflict == null ? "" : conflict.pos().toShortString() + " (" + conflict.dimension().location() + ")";
        return new LinkHeader(status.ordinal(), LinkHeader.clip(detail), link.access().ordinal(), link.priority(),
                LinkHeader.clip(where), rejection == null ? -1 : rejection.ordinal(), link.isNetworkActive());
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
