package org.tinycore.colonybridge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.client.ui.BarChart;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.stats.StatsSummary;

import java.util.List;

/**
 * Aba "Estatísticas" da tela da ponte: três cartões de totais, gráfico de itens entregues por hora e
 * ranking dos itens mais entregues. Só desenha o {@link StatsSummary} recebido; nenhum cálculo aqui.
 */
final class StatsView {

    private static final int CARD_HEIGHT = 28;
    private static final int CHART_HEIGHT = 44;
    private static final int TOP_ROW = 20;

    private final Font font;
    /** Área do ranking na última renderização (para o tooltip com o nome do item). */
    private int topX;
    private int topY;
    private int topColumnWidth;

    StatsView(Font font) {
        this.font = font;
    }

    void render(GuiGraphics g, StatsSummary stats, int x, int y, int width) {
        StatsSummary.Totals t = stats.totals();
        int gap = 4;
        int cardWidth = (width - gap * 2) / 3;
        renderCard(g, x, y, cardWidth, Component.translatable("gui.tccolonybridge.stats.last_hour"),
                Component.translatable("gui.tccolonybridge.stats.items_requests",
                        compact(t.itemsLastHour()), compact(t.requestsLastHour())));
        renderCard(g, x + cardWidth + gap, y, cardWidth,
                Component.translatable("gui.tccolonybridge.stats.window", stats.windowHours()),
                Component.translatable("gui.tccolonybridge.stats.items_requests",
                        compact(t.itemsWindow()), compact(t.requestsWindow())));
        renderCard(g, x + (cardWidth + gap) * 2, y, cardWidth,
                Component.translatable("gui.tccolonybridge.stats.crafts"),
                Component.translatable("gui.tccolonybridge.stats.crafts_value",
                        compact(t.craftsStarted()), compact(t.craftsFailed())));

        int chartY = y + CARD_HEIGHT + 8;
        g.drawString(font, Component.translatable("gui.tccolonybridge.stats.chart", stats.windowHours()),
                x, chartY, UiColors.TEXT_MUTED, false);
        BarChart.render(g, x, chartY + 10, width, CHART_HEIGHT, stats.chart());

        renderTop(g, stats.top(), x, chartY + 10 + CHART_HEIGHT + 6, width);
    }

    private void renderCard(GuiGraphics g, int x, int y, int width, Component label, Component value) {
        g.fill(x, y, x + width, y + CARD_HEIGHT, UiColors.PANEL);
        g.fill(x, y, x + 2, y + CARD_HEIGHT, UiColors.ACCENT);
        drawFitted(g, label, x + 5, y + 4, width - 7, UiColors.TEXT_MUTED);
        drawFitted(g, value, x + 5, y + 16, width - 7, UiColors.TEXT);
    }

    /** Ranking em duas colunas: ícone + quantidade (nome no tooltip). */
    private void renderTop(GuiGraphics g, List<StatsSummary.Top> top, int x, int y, int width) {
        topX = x;
        topY = y;
        topColumnWidth = width / 2;
        if (top.isEmpty()) {
            g.drawString(font, Component.translatable("gui.tccolonybridge.stats.no_data"), x, y + 4,
                    UiColors.TEXT_MUTED, false);
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            int cx = x + (i % 2) * topColumnWidth;
            int cy = y + (i / 2) * TOP_ROW;
            StatsSummary.Top entry = top.get(i);
            g.drawString(font, (i + 1) + ".", cx, cy + 4, UiColors.ACCENT, false);
            g.renderItem(new ItemStack(entry.item()), cx + 12, cy);
            g.drawString(font, compact(entry.count()), cx + 31, cy + 4, UiColors.TEXT, false);
        }
    }

    /** Nome do item ao passar o mouse no ranking. */
    void renderTooltip(GuiGraphics g, List<StatsSummary.Top> top, int mouseX, int mouseY) {
        if (topColumnWidth <= 0 || mouseX < topX || mouseY < topY) {
            return;
        }
        int index = ((mouseY - topY) / TOP_ROW) * 2 + (mouseX - topX) / topColumnWidth;
        if ((mouseX - topX) / topColumnWidth > 1 || index >= top.size()) {
            return;
        }
        g.renderTooltip(font, new ItemStack(top.get(index).item()), mouseX, mouseY);
    }

    /** Texto reduzido em escala se passar da largura (cartões são estreitos). */
    private void drawFitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        float scale = w > maxWidth ? (float) maxWidth / w : 1f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** 1234 → "1.2k", 3400000 → "3.4M" (cabe nos cartões). */
    static String compact(long value) {
        if (value < 1_000) {
            return Long.toString(value);
        }
        if (value < 1_000_000) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return String.format("%.1fM", value / 1_000_000.0);
    }
}
