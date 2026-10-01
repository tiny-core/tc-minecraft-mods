package org.tinycore.cloud.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Cache dos itens da nuvem neste servidor, nos dois sentidos:
 * <ul>
 *   <li>{@link #describe}: {@code ItemStack} do mundo → bytes + impressão digital (o AE2 pergunta o mesmo item
 *       muitas vezes por tick; codificar a cada chamada seria caro). LRU com teto.</li>
 *   <li>{@link #resolve}: item vindo da nuvem → situação neste servidor e o protótipo (quantidade 1). Uma
 *       decodificação por item enquanto o servidor estiver de pé.</li>
 * </ul>
 * Só usado na thread do servidor.
 */
public final class ItemCatalog {

    /** Item do mundo descrito para a nuvem. {@code item == null} quando {@code rejection} explica por quê. */
    public record Described(@Nullable EncodedItem item, @Nullable TransferRejection rejection,
                            @Nullable String worldReference) {}

    /** Item da nuvem visto deste servidor. {@code prototype != null} só quando decodificou. */
    public record Resolved(@NotNull EncodedItem item, @NotNull ItemCompatibility decodeStatus,
                           @Nullable ItemStack prototype) {}

    private final HolderLookup.Provider registries;
    private final Predicate<String> isModLoaded;
    private final IntSupplier maxBytes;
    private final WorldReferenceDetector detector;
    private final Map<ItemIdentity, Described> described;
    private final Map<String, Resolved> resolved = new HashMap<>();

    /**
     * @param maxCached teto do cache de {@link #describe} (LRU)
     */
    public ItemCatalog(@NotNull HolderLookup.Provider registries, @NotNull Predicate<String> isModLoaded,
                       @NotNull IntSupplier maxBytes, @NotNull WorldReferenceDetector detector, int maxCached) {
        this.registries = registries;
        this.isModLoaded = isModLoaded;
        this.maxBytes = maxBytes;
        this.detector = detector;
        // LinkedHashMap com accessOrder=true + removeEldestEntry = cache LRU de tamanho fixo.
        this.described = new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ItemIdentity, Described> eldest) {
                return size() > maxCached;
            }
        };
    }

    /** Descreve um item do mundo (ignora a quantidade). */
    public @NotNull Described describe(@NotNull ItemStack stack) {
        return described.computeIfAbsent(ItemIdentity.of(stack), identity -> encode(stack));
    }

    private Described encode(ItemStack stack) {
        ItemCodec.Encoded encoded = ItemCodec.encode(stack, registries, maxBytes.getAsInt());
        if (!encoded.ok()) return new Described(null, encoded.rejection(), null);
        CompoundTag components = encoded.tag() != null ? encoded.tag().getCompound("components") : new CompoundTag();
        String evidence = detector.find(components).orElse(null);
        EncodedItem item = encoded.item();
        // Veio de um ItemStack real deste jogo: já sabemos que decodifica, sem gastar uma decodificação.
        resolved.putIfAbsent(item.fingerprint(), new Resolved(item, ItemCompatibility.OK, stack.copyWithCount(1)));
        return new Described(item, null, evidence);
    }

    /** Situação neste servidor de um item vindo da nuvem (decodifica na primeira vez). */
    public @NotNull Resolved resolve(@NotNull EncodedItem item) {
        return resolved.computeIfAbsent(item.fingerprint(), fp -> {
            ItemCodec.Decoded decoded = ItemCodec.decode(item.bytes(), registries, isModLoaded);
            return new Resolved(item, decoded.status(), decoded.stack());
        });
    }

    /** Item já conhecido pela impressão digital, ou {@code null}. */
    public @Nullable Resolved byFingerprint(@NotNull String fingerprint) {
        return resolved.get(fingerprint);
    }
}
