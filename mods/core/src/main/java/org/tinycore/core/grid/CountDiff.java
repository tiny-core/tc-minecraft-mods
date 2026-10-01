package org.tinycore.core.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Regra pura de sincronização de grades de itens (Terminal do Armazém, TC Cloud Link): o que mudou entre duas contagens
 * (tipo de item → quantidade). O servidor manda ao cliente só essas mudanças, não a lista inteira a cada vez.
 * <p>
 * Genérica no tipo da chave ({@code <K>} ≈ generic de C#) para ser testada sem itens do Minecraft. Os
 * mapas decidem o que é "a mesma chave" (no jogo: item + componentes, ex.: {@code WarehouseItems} no Colony Bridge).
 */
public final class CountDiff {

    private CountDiff() {}

    /** Uma mudança: a chave passou a ter {@code count} (0 = a chave sumiu). */
    public record Change<K>(K key, long count) {}

    /**
     * Mudanças de {@code previous} para {@code current}: chaves novas ou com outra quantidade, e as
     * que sumiram (com quantidade 0). Ordem: primeiro as de {@code current}, depois as removidas.
     */
    public static <K> List<Change<K>> between(Map<K, Long> previous, Map<K, Long> current) {
        List<Change<K>> changes = new ArrayList<>();
        forEachChange(previous, current, (key, count) -> changes.add(new Change<>(key, count)));
        return changes;
    }

    /** Igual a {@link #between}, sem criar a lista (para o servidor, que pode ter milhares de tipos). */
    public static <K> void forEachChange(Map<K, Long> previous, Map<K, Long> current, BiConsumer<K, Long> sink) {
        for (Map.Entry<K, Long> entry : current.entrySet()) {
            Long before = previous.get(entry.getKey());
            if (before == null || !before.equals(entry.getValue())) {
                sink.accept(entry.getKey(), entry.getValue());
            }
        }
        for (K key : previous.keySet()) {
            if (!current.containsKey(key)) {
                sink.accept(key, 0L);
            }
        }
    }
}
