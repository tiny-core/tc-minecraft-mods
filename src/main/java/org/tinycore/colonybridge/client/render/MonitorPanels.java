package org.tinycore.colonybridge.client.render;

import net.minecraft.network.chat.Component;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.client.ui.BarChart;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.client.ui.UiFormat;
import org.tinycore.colonybridge.stats.StatsSummary;
import org.tinycore.colonybridge.stats.StatsSummary.Top;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * O que a tela do monitor mostra, a partir do {@link MonitorData}. Adapta-se ao tamanho:
 * <ul>
 *   <li>largura 1 bloco: estado e pedidos em aberto; 2: + itens da última hora e nome da colônia;
 *       3+: + itens da janela inteira;</li>
 *   <li>altura 2 blocos: + lista paginada de pedidos ({@link MonitorRequestList});
 *       3+: gráfico por hora; 4+: + faixa com os itens mais entregues.</li>
 * </ul>
 * Números e barras são suavizados pelo {@link MonitorAnimator} (cada série tem um índice fixo).
 * Medidas em "pixels de tela" ({@link MonitorRenderer#PIXELS_PER_BLOCK} por bloco).
 */
final class MonitorPanels {

    private static final int MARGIN = 4;
    private static final int HEADER = 14;
    private static final int BLOCK = MonitorRenderer.PIXELS_PER_BLOCK;

    private MonitorPanels() {}

    /** Altura do gráfico quando divide espaço com a lista. */
    private static final int CHART_HEIGHT = 40;
    /** Altura da faixa de itens mais entregues. */
    private static final int TOP_STRIP_HEIGHT = 20;

    /** Índices das séries animadas: 3 números, depois uma por barra do gráfico. */
    private static final int SERIES_METRICS = 0;
    private static final int SERIES_CHART = 8;

    /**
     * @param pageFor dado o número de páginas, devolve a página atual (vem do block entity mestre)
     * @param anim    suavização dos números e barras desta tela
     */
    static void render(MonitorCanvas c, int width, int height, MonitorData data, IntUnaryOperator pageFor,
                       MonitorAnimator anim) {
        c.fill(0, 0, width, height, UiColors.BACKGROUND | 0xFF000000, 0);
        c.fill(0, 0, width, 2, UiColors.ACCENT, 1);
        switch (data.link()) {
            case UNLINKED -> message(c, width, height, Component.translatable("monitor.tccolonybridge.unlinked"),
                    Component.translatable("monitor.tccolonybridge.unlinked_hint"), UiColors.TEXT_MUTED);
            case BRIDGE_MISSING -> message(c, width, height, Component.translatable("monitor.tccolonybridge.missing"),
                    Component.empty(), UiColors.DANGER);
            case OK -> dashboard(c, width, height, data, pageFor, anim);
        }
    }

    private static void message(MonitorCanvas c, int width, int height, Component title, Component hint, int color) {
        float y = height / 2f - 8;
        y += c.textCentered(title, width / 2f, y, color, 1f, width - MARGIN * 2, 2) + 3;
        c.textCentered(hint, width / 2f, y, UiColors.TEXT_MUTED, 0.8f, width - MARGIN * 2, 2);
    }

    private static void dashboard(MonitorCanvas c, int width, int height, MonitorData data,
                                  IntUnaryOperator pageFor, MonitorAnimator anim) {
        header(c, width, data);
        StatsSummary.Totals totals = data.stats().totals();
        int columns = Math.min(3, Math.max(1, width / BLOCK));
        float columnWidth = (width - MARGIN * (columns + 1)) / (float) columns;
        float top = HEADER + MARGIN + 2;
        metric(c, MARGIN, top, columnWidth, Component.translatable("monitor.tccolonybridge.open_requests"),
                anim.value(SERIES_METRICS, data.openRequests()));
        if (columns >= 2) {
            metric(c, MARGIN * 2 + columnWidth, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.items_hour"),
                    anim.value(SERIES_METRICS + 1, totals.itemsLastHour()));
        }
        if (columns >= 3) {
            metric(c, MARGIN * 3 + columnWidth * 2, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.items_window", data.stats().windowHours()),
                    anim.value(SERIES_METRICS + 2, totals.itemsWindow()));
        }
        float next = top + 30;
        if (height < BLOCK * 2) {
            return; // 1 bloco de altura: só os números
        }
        float inner = width - MARGIN * 2;
        if (height >= BLOCK * 3) {
            BarChart.render(c, MARGIN, next, inner, CHART_HEIGHT, animatedChart(data, anim), 1);
            next += CHART_HEIGHT + 6;
        }
        if (height >= BLOCK * 4 && !data.stats().top().isEmpty()) {
            topStrip(c, MARGIN, next, inner, data.stats().top());
            next += TOP_STRIP_HEIGHT + 4;
        }
        float listHeight = height - next - MARGIN;
        int pages = MonitorRequestList.pages(data.requests().size(), inner, listHeight);
        MonitorRequestList.render(c, data.requests(), data.openRequests(), pageFor.applyAsInt(pages),
                MARGIN, next, inner, listHeight);
    }

    /** Mesmas barras do gráfico, suavizadas (uma série animada por barra). */
    private static float[] animatedChart(MonitorData data, MonitorAnimator anim) {
        List<Integer> chart = data.stats().chart();
        float[] values = new float[chart.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = anim.value(SERIES_CHART + i, chart.get(i));
        }
        return values;
    }

    /** Faixa horizontal com os itens mais entregues: ícone + quantidade, lado a lado. */
    private static void topStrip(MonitorCanvas c, float x, float y, float width, List<Top> top) {
        c.fill(x, y, x + width, y + TOP_STRIP_HEIGHT, UiColors.PANEL, 1);
        float labelWidth = width * 0.2f;
        c.textFitted(Component.translatable("monitor.tccolonybridge.top_items"), x + 3, y + 7,
                UiColors.TEXT_MUTED, 0.6f, labelWidth, 2);
        float cellX = x + labelWidth + 6;
        float cell = (x + width - 2 - cellX) / top.size();
        for (int i = 0; i < top.size(); i++) {
            Top entry = top.get(i);
            c.item(MonitorRequestList.icon(entry.item()), cellX + i * cell, y + 2, 15, 2);
            c.textFitted(Component.literal(UiFormat.compact(entry.count())), cellX + i * cell + 17, y + 7,
                    UiColors.TEXT, 0.6f, cell - 19, 2);
        }
    }

    /** Faixa do topo: nome da colônia (se couber) à esquerda, estado à direita com a cor dele. */
    private static void header(MonitorCanvas c, int width, MonitorData data) {
        c.fill(0, 2, width, HEADER, UiColors.PANEL, 1);
        Component status = Component.translatable(data.status().guiKey());
        int color = StatusColors.of(data.status());
        if (width < BLOCK * 2) {
            c.textCentered(status, width / 2f, 4, color, 0.8f, width - MARGIN * 2, 2);
            return;
        }
        float statusWidth = Math.min(c.width(status) * 0.8f, width * 0.4f);
        c.textFitted(status, width - MARGIN - statusWidth, 4, color, 0.8f, statusWidth, 2);
        c.textFitted(Component.literal(data.colonyName()), MARGIN, 4, UiColors.HIGHLIGHT, 0.8f,
                width - statusWidth - MARGIN * 3, 2);
    }

    /** Cartão de número: rótulo pequeno em cima, valor grande embaixo. */
    private static void metric(MonitorCanvas c, float x, float y, float width, Component label, float value) {
        c.fill(x, y, x + width, y + 26, UiColors.PANEL, 1);
        c.fill(x, y, x + 1.5f, y + 26, UiColors.ACCENT, 2);
        c.textFitted(label, x + 4, y + 3, UiColors.TEXT_MUTED, 0.6f, width - 6, 2);
        c.textFitted(Component.literal(UiFormat.compact(Math.round(value))), x + 4, y + 11, UiColors.TEXT,
                1.4f, width - 6, 2);
    }
}
