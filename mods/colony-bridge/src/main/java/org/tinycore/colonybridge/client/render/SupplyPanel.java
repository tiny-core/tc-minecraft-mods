package org.tinycore.colonybridge.client.render;

import net.minecraft.network.chat.Component;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.StockLine;
import org.tinycore.colonybridge.block.monitor.SupplyContent;
import org.tinycore.colonybridge.stats.SupplySummary;
import org.tinycore.core.client.ui.BarChart;
import org.tinycore.core.client.ui.UiColors;
import org.tinycore.core.client.ui.UiFormat;

import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.tinycore.colonybridge.client.render.MonitorPanels.BLOCK;
import static org.tinycore.colonybridge.client.render.MonitorPanels.CHART_HEIGHT;
import static org.tinycore.colonybridge.client.render.MonitorPanels.HEADER;
import static org.tinycore.colonybridge.client.render.MonitorPanels.MARGIN;
import static org.tinycore.colonybridge.client.render.MonitorPanels.SERIES_METRICS;

/**
 * Painel do Abastecedor no monitor. Mesmo cabeçalho e cartões do painel da Ponte ({@link MonitorPanels}),
 * com os números do Abastecedor, e adapta-se ao tamanho:
 * <ul>
 *   <li>largura 1 bloco: linhas abaixo do alvo; 2: + repostos (1h); 3: + devolvidos ao ME (1h);
 *       4+: + repostos na janela inteira;</li>
 *   <li>altura 2 blocos: + lista das linhas com barra atual/alvo; 3+: + gráfico de repostos por hora.</li>
 * </ul>
 * Cada linha da lista mostra ícone, quantidade atual, a direção com o limite à direita ("↓ mín 64" = vem da
 * rede ME até ter pelo menos 64; "↑ máx 64" = o que passar de 64 volta para o ME) e uma barra colorida: âmbar = abaixo do alvo (falta
 * repor), verde = ok, ciano = acima do alvo (excedente que vai voltar para o ME). A lista é paginada como
 * a de pedidos (página vem do block entity: troca automática + clique).
 * <p>
 * {@code import static} traz as constantes do {@link MonitorPanels} sem prefixo (≈ {@code using static} em C#).
 */
final class SupplyPanel {

    private static final int ROW_HEIGHT = 16;
    private static final int FOOTER = 10;
    private static final int GAP = 3;
    /** Duas colunas de linhas a partir de 4 blocos de largura. */
    private static final int TWO_COLUMNS_FROM = BLOCK * 4;

    private SupplyPanel() {}

    static void render(MonitorCanvas c, int width, int height, MonitorData data, SupplyContent supply,
                       IntUnaryOperator pageFor, MonitorAnimator anim) {
        MonitorPanels.header(c, width, data);
        SupplySummary stats = supply.stats();
        int columns = Math.min(4, Math.max(1, width / BLOCK));
        float columnWidth = (width - MARGIN * (columns + 1)) / (float) columns;
        float top = HEADER + MARGIN + 2;
        MonitorPanels.metric(c, MARGIN, top, columnWidth, Component.translatable("monitor.tccolonybridge.supply.below"),
                anim.value(SERIES_METRICS, supply.belowTarget()));
        if (columns >= 2) {
            MonitorPanels.metric(c, MARGIN * 2 + columnWidth, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.supply.restocked_hour"),
                    anim.value(SERIES_METRICS + 1, stats.restockedLastHour()));
        }
        if (columns >= 3) {
            MonitorPanels.metric(c, MARGIN * 3 + columnWidth * 2, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.supply.returned_hour"),
                    anim.value(SERIES_METRICS + 2, stats.returnedLastHour()));
        }
        if (columns >= 4) {
            MonitorPanels.metric(c, MARGIN * 4 + columnWidth * 3, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.supply.restocked_window", stats.windowHours()),
                    anim.value(SERIES_METRICS + 3, stats.restockedWindow()));
        }
        float next = top + 30;
        if (height < BLOCK * 2) {
            return; // 1 bloco de altura: só os números
        }
        float inner = width - MARGIN * 2;
        if (height >= BLOCK * 3) {
            BarChart.render(c, MARGIN, next, inner, CHART_HEIGHT,
                    MonitorPanels.animatedChart(stats.chart(), anim), 1);
            next += CHART_HEIGHT + 6;
        }
        renderLines(c, supply.lines(), pageFor, MARGIN, next, inner, height - next - MARGIN);
    }

    /** Lista paginada das linhas configuradas (1 ou 2 colunas, conforme a largura). */
    private static void renderLines(MonitorCanvas c, List<StockLine> lines, IntUnaryOperator pageFor,
                                    float x, float y, float width, float height) {
        if (lines.isEmpty()) {
            c.textCentered(Component.translatable("monitor.tccolonybridge.supply.empty"), x + width / 2f,
                    y + height / 2f - 4, UiColors.TEXT_MUTED, 0.8f, width, 2);
            return;
        }
        int columns = width >= TWO_COLUMNS_FROM ? 2 : 1;
        int rows = Math.max(0, (int) ((height - FOOTER) / ROW_HEIGHT));
        int perPage = rows * columns;
        if (perPage == 0) {
            return;
        }
        int pages = (lines.size() + perPage - 1) / perPage;
        int page = pageFor.applyAsInt(pages);
        float columnWidth = (width - GAP * (columns - 1)) / columns;
        int first = page * perPage;
        for (int i = 0; i < perPage && first + i < lines.size(); i++) {
            int column = i / rows;
            int row = i % rows;
            row(c, lines.get(first + i), x + column * (columnWidth + GAP), y + row * ROW_HEIGHT, columnWidth);
        }
        if (pages > 1) {
            c.textCentered(Component.literal("<  " + (page + 1) + "/" + pages + "  >"), x + width / 2f,
                    y + height - FOOTER + 2, UiColors.TEXT_MUTED, 0.7f, width / 2f, 2);
        }
    }

    /** Ícone, "atual / alvo" e a barra de progresso colorida pela situação da linha. */
    private static void row(MonitorCanvas c, StockLine line, float x, float y, float width) {
        c.fill(x, y, x + width, y + ROW_HEIGHT - 2, UiColors.PANEL, 1);
        c.item(MonitorRequestList.icon(line.item()), x + 1, y + 1, 12, 2);
        int color = switch (line.state()) {
            case BELOW -> UiColors.WARNING;
            case OK -> UiColors.SUCCESS;
            case ABOVE -> UiColors.HIGHLIGHT;
        };
        float textX = x + 16;
        Component limit = Component.translatable(line.keep() ? "monitor.tccolonybridge.supply.min"
                : "monitor.tccolonybridge.supply.max", UiFormat.compact(line.target()));
        float limitWidth = Math.min(c.width(limit) * 0.6f, (width - 18) / 2f);
        float limitScale = limitWidth / Math.max(1, c.width(limit));
        c.text(limit, x + width - 2 - limitWidth, y + 2, UiColors.TEXT_MUTED, limitScale, 2);
        c.textFitted(Component.literal(UiFormat.compact(line.current())), textX, y + 2, UiColors.TEXT, 0.6f,
                width - 22 - limitWidth, 2);
        // Barra: fração atual/alvo, até 100% (acima do alvo a barra fica cheia e ciano).
        float barX = textX;
        float barWidth = width - 18;
        float barY = y + 9;
        float fraction = line.target() <= 0 ? 1f : Math.min(1f, (float) line.current() / line.target());
        // Camadas acima do fundo da linha (1): no mesmo plano, a placa de vídeo alterna entre os dois e a
        // barra vazia "pisca" conforme o jogador anda (z-fighting).
        c.fill(barX, barY, barX + barWidth, barY + 3, UiColors.BORDER, 2);
        c.fill(barX, barY, barX + barWidth * fraction, barY + 3, color, 3);
    }
}
