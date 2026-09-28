package org.tinycore.colonybridge.logic.bridge;

import org.tinycore.colonybridge.integration.OpenRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Relatório do último ciclo: o que aconteceu com cada pedido. Alimenta a tela da ponte.
 * <p>
 * Guarda no máximo {@link #MAX_LINES} linhas (limite de tamanho do pacote enviado ao cliente),
 * mas conta o total para a tela mostrar "+N pedidos".
 */
public final class CycleReport {

    /** Teto de linhas sincronizadas com o cliente (ver regras de segurança no CLAUDE.md). */
    public static final int MAX_LINES = 40;

    private final List<RequestLine> lines = new ArrayList<>();
    private int total;

    void clear() {
        lines.clear();
        total = 0;
    }

    void add(OpenRequest request, RequestOutcome outcome) {
        total++;
        if (lines.size() < MAX_LINES) {
            lines.add(new RequestLine(request.icon(), request.amount(), outcome, request.label()));
        }
    }

    /** Cópia imutável das linhas (seguro para guardar no snapshot). */
    public List<RequestLine> lines() {
        return List.copyOf(lines);
    }

    public int total() {
        return total;
    }
}
