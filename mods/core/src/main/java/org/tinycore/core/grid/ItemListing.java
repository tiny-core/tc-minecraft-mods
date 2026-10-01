package org.tinycore.core.grid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Regra pura de grades de itens (ex.: Terminal do Armazém do Colony Bridge, TC Cloud Link): filtra pela busca e ordena. Genérica ({@code <T>}) para ser
 * testada sem itens; a tela passa como ler o nome, o id do mod e a quantidade de cada entrada.
 * <p>
 * Busca como no AE2: texto comum procura no nome; começando com {@code @} procura no id do mod
 * ({@code @minecolonies}). Maiúsculas e minúsculas não importam.
 */
public final class ItemListing {

    /** Ordem da grade. */
    public enum Sort {
        /** Mais itens primeiro (empate: nome). */
        AMOUNT,
        /** Nome de A a Z. */
        NAME;

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Sort byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : AMOUNT;
        }
    }

    private ItemListing() {}

    /**
     * Entradas que passam na busca, na ordem pedida. Não altera a lista recebida.
     *
     * @param name   nome visível da entrada
     * @param modId  id do mod dono do item (ex.: "minecraft")
     * @param amount quantidade da entrada
     */
    public static <T> List<T> filterAndSort(List<T> entries, String query, Sort sort, Function<T, String> name,
                                            Function<T, String> modId, ToLongFunction<T> amount) {
        String search = query.trim().toLowerCase(Locale.ROOT);
        boolean byMod = search.startsWith("@");
        String term = byMod ? search.substring(1) : search;
        List<T> result = new ArrayList<>();
        for (T entry : entries) {
            String field = byMod ? modId.apply(entry) : name.apply(entry);
            if (term.isEmpty() || field.toLowerCase(Locale.ROOT).contains(term)) {
                result.add(entry);
            }
        }
        Comparator<T> byName = Comparator.comparing(entry -> name.apply(entry).toLowerCase(Locale.ROOT));
        Comparator<T> order = sort == Sort.NAME ? byName
                : Comparator.<T>comparingLong(amount).reversed().thenComparing(byName);
        result.sort(order);
        return result;
    }
}
