package org.tinycore.colonybridge.logic.supply;

import org.tinycore.colonybridge.logic.target.TargetList;

import java.util.Arrays;

/**
 * O que o último ciclo mediu em cada linha de <b>uma</b> lista do Abastecedor (Manter ou Excedente): quanto
 * há no armazém, quanto há na rede ME e a situação em palavras. A tela e o monitor leem daqui.
 * <p>
 * Vetores de tamanho fixo ({@link TargetList#HARD_MAX_LINES}) em vez de listas: memória constante e nenhuma
 * alocação por ciclo. Índice fora da faixa devolve "sem dados", porque o índice vem de listas que podem ter
 * mudado de tamanho desde o ciclo.
 */
public final class SupplyLineResults {

    private final long[] warehouse = new long[TargetList.HARD_MAX_LINES];
    private final long[] network = new long[TargetList.HARD_MAX_LINES];
    private final SupplyLineStatus[] status = new SupplyLineStatus[TargetList.HARD_MAX_LINES];

    SupplyLineResults() {
        clear();
    }

    void set(int line, long warehouseCount, long networkCount, SupplyLineStatus lineStatus) {
        if (inRange(line)) {
            warehouse[line] = warehouseCount;
            network[line] = networkCount;
            status[line] = lineStatus;
        }
    }

    /** Linha sem dados (desligada, inválida ou bloco parado). */
    void clear(int line) {
        set(line, 0, 0, SupplyLineStatus.UNKNOWN);
    }

    void clear() {
        Arrays.fill(warehouse, 0);
        Arrays.fill(network, 0);
        Arrays.fill(status, SupplyLineStatus.UNKNOWN);
    }

    public long warehouse(int line) {
        return inRange(line) ? warehouse[line] : 0;
    }

    public long network(int line) {
        return inRange(line) ? network[line] : 0;
    }

    public SupplyLineStatus status(int line) {
        return inRange(line) ? status[line] : SupplyLineStatus.UNKNOWN;
    }

    private static boolean inRange(int line) {
        return line >= 0 && line < TargetList.HARD_MAX_LINES;
    }
}
