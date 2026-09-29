package org.tinycore.colonybridge.stats;

import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Resumo pronto para exibir das estatísticas de uma ponte. Vai para os monitores dentro do
 * {@code MonitorData} (sincronizado como NBT do block entity). Só números e itens: compacto.
 *
 * @param windowHours tamanho da janela em horas (24 com a config padrão)
 * @param totals      somas da última hora e da janela inteira
 * @param chart       itens entregues por grupo de tempo, do mais antigo ao mais recente
 * @param top         itens mais entregues na janela
 */
public record StatsSummary(int windowHours, Totals totals, List<Integer> chart, List<Top> top) {

    /** Máximo de itens no ranking e de barras no gráfico (limite de tamanho do dado sincronizado). */
    public static final int MAX_TOP = 6;
    public static final int MAX_CHART = 48;

    public static final StatsSummary EMPTY = new StatsSummary(0, Totals.ZERO, List.of(), List.of());

    /**
     * @param craftsDone jobs de craft desta ponte que o AE2 terminou (itens entregues no armazém)
     */
    public record Totals(long itemsLastHour, long requestsLastHour, long itemsWindow, long requestsWindow,
                         long craftsStarted, long craftsFailed, long craftsDone) {

        public static final Totals ZERO = new Totals(0, 0, 0, 0, 0, 0, 0);
    }

    /** Item do ranking. */
    public record Top(Item item, long count) {}
}
