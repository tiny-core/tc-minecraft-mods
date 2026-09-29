package org.tinycore.colonybridge.stats;

import java.util.List;

/**
 * Resumo pronto para exibir das estatísticas de um Abastecedor (vai para os monitores no
 * {@code MonitorData}).
 *
 * @param windowHours tamanho da janela em horas
 * @param chart       itens repostos por grupo de tempo, do mais antigo ao mais recente
 */
public record SupplySummary(int windowHours, long restockedLastHour, long returnedLastHour,
                            long restockedWindow, long returnedWindow, List<Integer> chart) {

    public static final SupplySummary EMPTY = new SupplySummary(0, 0, 0, 0, 0, List.of());
}
