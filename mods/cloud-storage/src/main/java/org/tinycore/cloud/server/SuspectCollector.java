package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.integration.tcmine.CloudBackend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta os itens recusados por parecerem guardar dados no mundo, para mandar ao TCMine de tempos em tempos (com
 * o heartbeat), e não um pedido por recusa. O AE2 pergunta "aceita?" muitas vezes pelo mesmo item; só a
 * tentativa de verdade (não a simulação) conta como tentativa, mas a simulação já põe o item na fila.
 * Teto de itens por relatório para um mod com defeito não gerar um pedido gigante.
 */
final class SuspectCollector {

    static final int MAX_PER_REPORT = 200;

    private record Entry(String evidence, long attempts) {}

    private final Map<String, Entry> pending = new LinkedHashMap<>();

    void record(@NotNull String itemId, @NotNull String evidence, boolean simulate) {
        if (!pending.containsKey(itemId) && pending.size() >= MAX_PER_REPORT) return;
        Entry before = pending.get(itemId);
        long attempts = (before == null ? 0 : before.attempts()) + (simulate ? 0 : 1);
        pending.put(itemId, new Entry(evidence, attempts));
    }

    /** Esvazia a fila; a lista volta com {@link #restore} se o envio falhar. */
    @NotNull List<CloudBackend.SuspectReport> drain() {
        List<CloudBackend.SuspectReport> reports = new ArrayList<>(pending.size());
        pending.forEach((id, e) -> reports.add(new CloudBackend.SuspectReport(id, e.evidence(), Math.max(1, e.attempts()))));
        pending.clear();
        return reports;
    }

    void restore(@NotNull List<CloudBackend.SuspectReport> reports) {
        for (CloudBackend.SuspectReport r : reports) {
            Entry now = pending.get(r.itemId());
            pending.put(r.itemId(), new Entry(r.evidence(), r.attempts() + (now == null ? 0 : now.attempts())));
        }
    }
}
