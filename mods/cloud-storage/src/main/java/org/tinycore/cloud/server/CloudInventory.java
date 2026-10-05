package org.tinycore.cloud.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.cloud.PlayerCloudSession;
import org.tinycore.cloud.integration.ae2.Ae2CellProbe;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.ItemCatalog;
import org.tinycore.cloud.item.ItemCompatibility;
import org.tinycore.cloud.item.TransferGuard;
import org.tinycore.cloud.item.TransferRejection;
import org.tinycore.cloud.item.VanillaContentProbe;
import org.tinycore.cloud.item.WorldReferenceDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guardar, retirar e listar itens de um canal: a ponte entre {@code ItemStack} (mundo, AE2, tela) e o núcleo da
 * nuvem ({@link PlayerCloudSession}, que só conhece impressões digitais). Passa tudo pelo {@link TransferGuard}.
 *
 * <p>Mesmo contrato do AE2: {@code simulate=true} só calcula, sem mudar nada. Só na thread do servidor.
 */
public final class CloudInventory {

    /** Resultado de {@link #insert}: quanto entrou e, se nada entrou por regra, o motivo. */
    public record InsertResult(long accepted, @Nullable TransferRejection rejection) {}

    private final CloudService service;
    private final ItemCatalog catalog;
    private final TransferGuard guard;

    CloudInventory(@NotNull CloudService service, @NotNull HolderLookup.Provider registries) {
        this.service = service;
        this.catalog = new ItemCatalog(registries, ModList.get()::isLoaded, service::maxItemBytes,
                WorldReferenceDetector.withDefaults(), Config.CATALOG_CACHE_SIZE.get());
        this.guard = new TransferGuard(service::policy, List.of(new VanillaContentProbe(), new Ae2CellProbe()));
    }

    public @NotNull InsertResult insert(@NotNull UUID player, @NotNull UUID channel, @NotNull ItemStack stack,
                                        long amount, boolean simulate) {
        PlayerCloudSession session = service.session(player);
        if (session == null || stack.isEmpty() || amount <= 0) return new InsertResult(0, null);
        ItemCatalog.Described described = catalog.describe(stack);
        TransferRejection rejection = guard.checkInsert(stack, described);
        if (rejection == TransferRejection.WORLD_REFERENCE && described.item() != null) {
            service.recordSuspect(described.item().itemId(), String.valueOf(described.worldReference()), simulate);
        }
        if (rejection != null) return new InsertResult(0, rejection);
        EncodedItem item = described.item();
        service.remember(item);
        long accepted = session.insert(new BalanceKey(channel, item.fingerprint()), amount, simulate);
        return new InsertResult(accepted, null);
    }

    /** Retira pela impressão digital. Item que não está {@code OK} neste servidor nunca sai. */
    public long extract(@NotNull UUID player, @NotNull UUID channel, @NotNull String fingerprint, long amount,
                        boolean simulate) {
        PlayerCloudSession session = service.session(player);
        if (session == null || amount <= 0) return 0;
        if (status(fingerprint) != ItemCompatibility.OK) return 0;
        return session.extract(new BalanceKey(channel, fingerprint), amount, simulate);
    }

    /** Impressão digital de um item do mundo, ou {@code null} se ele nem codifica. */
    public @Nullable String fingerprintOf(@NotNull ItemStack stack) {
        EncodedItem item = catalog.describe(stack).item();
        return item == null ? null : item.fingerprint();
    }

    /** O item (quantidade 1) pronto para sair, ou {@code null} se não pode sair deste servidor. */
    public @Nullable ItemStack prototype(@NotNull String fingerprint) {
        ItemCatalog.Resolved resolved = resolve(fingerprint);
        return resolved != null && status(resolved) == ItemCompatibility.OK ? resolved.prototype() : null;
    }

    /** Itens do canal com a situação de cada um neste servidor. */
    public @NotNull List<CloudEntry> entries(@NotNull UUID player, @NotNull UUID channel) {
        PlayerCloudSession session = service.session(player);
        if (session == null) return List.of();
        List<CloudEntry> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : session.balances().items(channel).entrySet()) {
            ItemCatalog.Resolved resolved = resolve(e.getKey());
            if (resolved == null) {
                result.add(new CloudEntry(e.getKey(), null, "?", "?", e.getValue(), ItemCompatibility.DATA_INVALID));
                continue;
            }
            EncodedItem item = resolved.item();
            result.add(new CloudEntry(e.getKey(), resolved.prototype(), item.itemId(), item.displayName(), e.getValue(),
                    status(resolved)));
        }
        return result;
    }

    private ItemCompatibility status(String fingerprint) {
        ItemCatalog.Resolved resolved = resolve(fingerprint);
        return resolved == null ? ItemCompatibility.DATA_INVALID : status(resolved);
    }

    private ItemCompatibility status(ItemCatalog.Resolved resolved) {
        if (resolved.decodeStatus() != ItemCompatibility.OK || resolved.prototype() == null) return resolved.decodeStatus();
        return guard.allowsExtract(resolved.prototype(), resolved.item().itemId()) ? ItemCompatibility.OK
                : ItemCompatibility.BLOCKED;
    }

    private @Nullable ItemCatalog.Resolved resolve(String fingerprint) {
        ItemCatalog.Resolved known = catalog.byFingerprint(fingerprint);
        if (known != null) return known;
        EncodedItem item = service.definition(fingerprint);
        return item == null ? null : catalog.resolve(item);
    }
}
