package org.tinycore.cloud.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.block.NetworkAccess;
import org.tinycore.cloud.cloud.PlayerCloudSession;
import org.tinycore.cloud.item.ItemCompatibility;
import org.tinycore.cloud.server.CloudEntry;
import org.tinycore.cloud.server.CloudService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * O canal da nuvem visto pela rede AE2, como se fosse um disco ({@code MEStorage}). Montado pelo TC Cloud Link
 * só enquanto o dono está online e com o lease (plano §7).
 *
 * <p>Tudo passa por {@code CloudInventory}: o {@code TransferGuard} decide o que entra, e só itens {@code OK}
 * neste servidor aparecem e saem. O AE2 chama {@link #getAvailableStacks} muitas vezes: a lista fica em cache e
 * só é refeita quando o canal muda ({@code changeCount} da sessão).
 */
final class CloudMEStorage implements MEStorage {

    private final UUID owner;
    private final UUID channel;
    private final Supplier<NetworkAccess> access;
    private List<Cached> cache = List.of();
    private long cacheVersion = -1;
    private PlayerCloudSession cacheSession;

    private record Cached(AEItemKey key, long amount) {}

    CloudMEStorage(@NotNull UUID owner, @NotNull UUID channel, @NotNull Supplier<NetworkAccess> access) {
        this.owner = owner;
        this.channel = channel;
        this.access = access;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        CloudService service = CloudService.get();
        if (service == null || !(what instanceof AEItemKey itemKey) || !access.get().allowsInsert()) return 0;
        return service.inventory().insert(owner, channel, itemKey.toStack(), amount, mode == Actionable.SIMULATE).accepted();
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        CloudService service = CloudService.get();
        if (service == null || !(what instanceof AEItemKey itemKey) || !access.get().allowsExtract()) return 0;
        String fingerprint = service.inventory().fingerprintOf(itemKey.toStack());
        if (fingerprint == null) return 0;
        return service.inventory().extract(owner, channel, fingerprint, amount, mode == Actionable.SIMULATE);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (!access.get().allowsExtract()) return; // "somente depósito": a rede não enxerga o que dá para tirar
        for (Cached c : cached()) out.add(c.key(), c.amount());
    }

    @Override
    public Component getDescription() {
        return Component.translatable("block.tccloud.cloud_link");
    }

    private List<Cached> cached() {
        CloudService service = CloudService.get();
        PlayerCloudSession session = service == null ? null : service.session(owner);
        if (session == null) return List.of();
        if (session == cacheSession && session.changeCount() == cacheVersion) return cache;
        List<Cached> fresh = new ArrayList<>();
        for (CloudEntry entry : service.inventory().entries(owner, channel)) {
            ItemStack prototype = entry.prototype();
            if (entry.status() == ItemCompatibility.OK && prototype != null) {
                fresh.add(new Cached(AEItemKey.of(prototype), entry.amount()));
            }
        }
        cache = fresh;
        cacheSession = session;
        cacheVersion = session.changeCount();
        return cache;
    }
}
