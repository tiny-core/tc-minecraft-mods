package org.tinycore.colonybridge.client.render;

import net.minecraft.network.chat.Component;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.client.ui.BarChart;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.client.ui.UiFormat;
import org.tinycore.colonybridge.stats.StatsSummary;

/**
 * O que a tela do monitor mostra, a partir do {@link MonitorData}. Adapta-se ao tamanho:
 * <ul>
 *   <li>1 bloco de largura: estado e pedidos em aberto;</li>
 *   <li>2 blocos: + itens da última hora e nome da colônia no topo;</li>
 *   <li>3+ blocos: + itens da janela inteira; com 2+ de altura, gráfico por hora.</li>
 * </ul>
 * Medidas em "pixels de tela" ({@link MonitorRenderer#PIXELS_PER_BLOCK} por bloco).
 */
final class MonitorPanels {

    private static final int MARGIN = 4;
    private static final int HEADER = 14;
    private static final int BLOCK = MonitorRenderer.PIXELS_PER_BLOCK;

    private MonitorPanels() {}

    static void render(MonitorCanvas c, int width, int height, MonitorData data) {
        c.fill(0, 0, width, height, UiColors.BACKGROUND | 0xFF000000, 0);
        c.fill(0, 0, width, 2, UiColors.ACCENT, 1);
        switch (data.link()) {
            case UNLINKED -> message(c, width, height, Component.translatable("monitor.tccolonybridge.unlinked"),
                    Component.translatable("monitor.tccolonybridge.unlinked_hint"), UiColors.TEXT_MUTED);
            case BRIDGE_MISSING -> message(c, width, height, Component.translatable("monitor.tccolonybridge.missing"),
                    Component.empty(), UiColors.DANGER);
            case OK -> dashboard(c, width, height, data);
        }
    }

    private static void message(MonitorCanvas c, int width, int height, Component title, Component hint, int color) {
        float y = height / 2f - 8;
        y += c.textCentered(title, width / 2f, y, color, 1f, width - MARGIN * 2, 2) + 3;
        c.textCentered(hint, width / 2f, y, UiColors.TEXT_MUTED, 0.8f, width - MARGIN * 2, 2);
    }

    private static void dashboard(MonitorCanvas c, int width, int height, MonitorData data) {
        header(c, width, data);
        StatsSummary.Totals totals = data.stats().totals();
        int columns = Math.min(3, Math.max(1, width / BLOCK));
        float columnWidth = (width - MARGIN * (columns + 1)) / (float) columns;
        float top = HEADER + MARGIN + 2;
        metric(c, MARGIN, top, columnWidth, Component.translatable("monitor.tccolonybridge.open_requests"),
                data.openRequests());
        if (columns >= 2) {
            metric(c, MARGIN * 2 + columnWidth, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.items_hour"), totals.itemsLastHour());
        }
        if (columns >= 3) {
            metric(c, MARGIN * 3 + columnWidth * 2, top, columnWidth,
                    Component.translatable("monitor.tccolonybridge.items_window", data.stats().windowHours()),
                    totals.itemsWindow());
        }
        float chartTop = top + 30;
        if (height >= BLOCK * 2 && height - chartTop - MARGIN > 20) {
            BarChart.render(c, MARGIN, chartTop, width - MARGIN * 2, height - chartTop - MARGIN,
                    data.stats().chart(), 1);
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
    private static void metric(MonitorCanvas c, float x, float y, float width, Component label, long value) {
        c.fill(x, y, x + width, y + 26, UiColors.PANEL, 1);
        c.fill(x, y, x + 1.5f, y + 26, UiColors.ACCENT, 2);
        c.textFitted(label, x + 4, y + 3, UiColors.TEXT_MUTED, 0.6f, width - 6, 2);
        c.textFitted(Component.literal(UiFormat.compact(value)), x + 4, y + 11, UiColors.TEXT, 1.4f, width - 6, 2);
    }
}
