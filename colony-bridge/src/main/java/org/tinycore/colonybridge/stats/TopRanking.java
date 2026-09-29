package org.tinycore.colonybridge.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranking aproximado numa janela de tempo, com memória limitada: a regra por trás do {@link TopItems}.
 * <p>
 * É genérico ({@code <K>} ≈ genérico de C#; aqui {@code K} é o que se conta — no jogo, {@code Item}) para
 * poder ser testado sem o Minecraft. A janela é dividida em {@code groups} grupos (ex.: 24 horas); cada
 * grupo guarda no máximo {@code perGroup} chaves. Quando um grupo enche, a chave com menor contagem sai.
 * O resultado é <b>aproximado</b> (um item raro numa hora movimentada pode ficar de fora), em troca de
 * tamanho fixo na memória e no NBT.
 */
final class TopRanking<K> {

    /** Chave e quantidade (resultado do ranking). */
    record Entry<K>(K key, long count) {}

    private final int perGroup;
    private final List<Map<K, Long>> groups;
    /** Grupo mais recente já visto (-1 = nenhum): grupos mais antigos que a janela são limpos ao avançar. */
    private long current = -1;

    TopRanking(int groups, int perGroup) {
        this.perGroup = perGroup;
        this.groups = new ArrayList<>(groups);
        for (int i = 0; i < groups; i++) {
            this.groups.add(new HashMap<>());
        }
    }

    /** Soma {@code amount} à chave no grupo de tempo {@code group}. Grupos anteriores ao atual são ignorados. */
    void add(long group, K key, long amount) {
        advance(group);
        if (group <= current - groups.size()) {
            return; // já saiu da janela
        }
        Map<K, Long> counts = groups.get(index(group));
        counts.merge(key, amount, Long::sum);
        if (counts.size() > perGroup) {
            counts.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(smallest -> counts.remove(smallest.getKey()));
        }
    }

    /** As {@code limit} chaves com maior soma na janela que termina em {@code nowGroup}. */
    List<Entry<K>> top(int limit, long nowGroup) {
        advance(nowGroup);
        Map<K, Long> total = new HashMap<>();
        for (Map<K, Long> counts : groups) {
            counts.forEach((key, count) -> total.merge(key, count, Long::sum));
        }
        return total.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(e -> new Entry<>(e.getKey(), e.getValue()))
                .toList();
    }

    /** Anda o relógio até {@code group}, limpando os grupos que o tempo reaproveitou. */
    private void advance(long group) {
        if (group <= current) {
            return;
        }
        long steps = current < 0 ? groups.size() : Math.min(group - current, groups.size());
        for (long i = 0; i < steps; i++) {
            groups.get(index(group - i)).clear();
        }
        current = group;
    }

    private int index(long group) {
        return (int) Math.floorMod(group, (long) groups.size());
    }

    // --- acesso para salvar/carregar (o formato do NBT fica no TopItems) ---

    long current() {
        return current;
    }

    /** Contagens do grupo na posição {@code index} do anel (0..groupCount-1). */
    Map<K, Long> group(int index) {
        return groups.get(index);
    }

    /** Apaga tudo e posiciona o relógio (usado ao carregar do NBT). */
    void reset(long current) {
        groups.forEach(Map::clear);
        this.current = current;
    }
}
