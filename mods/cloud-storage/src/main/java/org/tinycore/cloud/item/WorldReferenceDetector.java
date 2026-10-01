package org.tinycore.cloud.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heurística da regra 5 do {@code TransferGuard} (plano §6): procura nos componentes de um item sinais de
 * que o conteúdo dele mora no save do mundo, e não no próprio item (ex.: mochila ou disco que guarda só um
 * UUID). Um item assim, levado para outro mundo, chega vazio, ou deixa o conteúdo no mundo antigo.
 *
 * <p>Sinais procurados, em qualquer profundidade:
 * <ul>
 *   <li>array de 4 inteiros (é como o Minecraft grava um UUID em NBT);</li>
 *   <li>texto no formato de UUID;</li>
 *   <li>chave com nome suspeito ({@code uuid}, {@code storage_id}, {@code frequency}...).</li>
 * </ul>
 * É heurística: pode dar falso positivo (ex.: item vinculado ao dono). Por isso o resultado não proíbe
 * para sempre; o item é recusado e reportado como suspeito, e o dono decide no painel.
 */
public final class WorldReferenceDetector {

    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Componentes com UUID que não referenciam o mundo (cabeça de jogador guarda o perfil). */
    public static final Set<String> DEFAULT_IGNORED_COMPONENTS = Set.of("minecraft:profile");

    /** Trechos de nome de chave que indicam ligação com dados guardados fora do item. */
    public static final Set<String> DEFAULT_SUSPICIOUS_KEYS = Set.of("uuid", "storage_id", "storageid", "frequency");

    private final Set<String> ignoredComponents;
    private final Set<String> suspiciousKeys;

    public WorldReferenceDetector(@NotNull Set<String> ignoredComponents, @NotNull Set<String> suspiciousKeys) {
        this.ignoredComponents = Set.copyOf(ignoredComponents);
        this.suspiciousKeys = suspiciousKeys.stream().map(k -> k.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static @NotNull WorldReferenceDetector withDefaults() {
        return new WorldReferenceDetector(DEFAULT_IGNORED_COMPONENTS, DEFAULT_SUSPICIOUS_KEYS);
    }

    /**
     * @param components o mapa de componentes do item codificado (chave = id do componente, ex.:
     *                   {@code "sophisticatedbackpacks:storage_uuid"})
     * @return o caminho do primeiro sinal achado (para o relatório ao dono), ou vazio se nada suspeito
     */
    public @NotNull Optional<String> find(@NotNull CompoundTag components) {
        for (String component : components.getAllKeys()) {
            if (ignoredComponents.contains(component)) continue;
            Optional<String> hit = scan(component, components.get(component), component);
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    private Optional<String> scan(String key, Tag tag, String path) {
        if (isSuspiciousKey(key)) return Optional.of(path + " (nome da chave)");
        switch (tag) {
            case IntArrayTag ints when ints.getAsIntArray().length == 4 -> {
                return Optional.of(path + " (UUID em int[4])");
            }
            case StringTag text when UUID_TEXT.matcher(text.getAsString()).find() -> {
                return Optional.of(path + " (UUID em texto)");
            }
            case CompoundTag compound -> {
                for (String child : compound.getAllKeys()) {
                    Optional<String> hit = scan(child, compound.get(child), path + "." + child);
                    if (hit.isPresent()) return hit;
                }
            }
            case ListTag list -> {
                for (int i = 0; i < list.size(); i++) {
                    Optional<String> hit = scan("", list.get(i), path + "[" + i + "]");
                    if (hit.isPresent()) return hit;
                }
            }
            default -> { }
        }
        return Optional.empty();
    }

    private boolean isSuspiciousKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        for (String fragment : suspiciousKeys) {
            if (lower.contains(fragment)) return true;
        }
        return false;
    }
}
