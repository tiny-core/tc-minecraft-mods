package org.tinycore.colonybridge.client.render;

import net.minecraft.network.chat.Component;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.StockLine;
import org.tinycore.colonybridge.block.monitor.SupplyContent;
import org.tinycore.colonybridge.logic.supply.SupplyLineStatus;
import org.tinycore.colonybridge.stats.SupplySummary;
import org.tinycore.core.client.ui.BarChart;
import org.tinycore.core.client.ui.UiColors;
import org.tinycore.core.client.ui.UiFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.tinycore.colonybridge.client.render.MonitorPanels.BLOCK;
import static org.tinycore.colonybridge.client.render.MonitorPanels.CHART_HEIGHT;
import static org.tinycore.colonybridge.client.render.MonitorPanels.HEADER;
import static org.tinycore.colonybridge.client.render.MonitorPanels.MARGIN;
import static org.tinycore.colonybridge.client.render.MonitorPanels.SERIES_METRICS;

/**
 * Painel do Abastecedor no monitor. A ideia é o jogador entender de relance <b>para onde</b> cada item vai
 * e <b>o que está acontecendo</b>, e não só ver números:
 * <ul>
 *   <li>cartões: situação geral ("Tudo certo" / "N precisam de atenção"), trazido do ME e enviado ao ME
 *       nas últimas 24 h e a última atividade — quantos aparecem depende da largura (1 a 4 blocos);</li>
 *   <li>lista em duas seções com título explicando a direção: "Manter no armazém · vem da rede ME" e
 *       "Excedente · volta para a rede ME" (lado a lado a partir de 4 blocos de largura);</li>
 *   <li>cada linha: ícone, nome, situação em palavras ({@link SupplyLineStatus}) na cor da gravidade,
 *       quanto há no armazém × meta/limite, quanto há na rede ME e uma barra;</li>
 *   <li>altura 3+ blocos: gráfico do que veio do ME por hora.</li>
 * </ul>
 * {@code import static} traz as constantes do {@link MonitorPanels} sem prefixo (≈ {@code using static} em C#).
 */
final class SupplyPanel {

    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_HEIGHT = 11;
    private static final int FOOTER = 10;
    private static final int GAP = 4;
    /** Seções lado a lado a partir de 4 blocos de largura. */
    private static final int TWO_COLUMNS_FROM = BLOCK * 4;

    private SupplyPanel() {}

    static void render(MonitorCanvas c, int width, int height, MonitorData data, SupplyContent supply,
                       IntUnaryOperator pageFor, MonitorAnimator anim) {
        MonitorPanels.header(c, width, data);
        float top = HEADER + MARGIN + 2;
        cards(c, width, top, supply, anim);
        float next = top + 30;
        if (height < BLOCK * 2) {
            return; // 1 bloco de altura: só os cartões
        }
        float inner = width - MARGIN * 2;
        if (height >= BLOCK * 3) {
            c.textFitted(Component.translatable("monitor.tccolonybridge.supply.chart"), MARGIN, next,
                    UiColors.TEXT_MUTED, 0.6f, inner, 2);
            next += 7;
            BarChart.render(c, MARGIN, next, inner, CHART_HEIGHT,
                    MonitorPanels.animatedChart(supply.stats().chart(), anim), 1);
            next += CHART_HEIGHT + 6;
        }
        lists(c, supply.lines(), pageFor, MARGIN, next, inner, height - next - MARGIN);
    }

    // ---------------------------------------------------------------- cartões

    private static void cards(MonitorCanvas c, int width, float top, SupplyContent supply, MonitorAnimator anim) {
        SupplySummary stats = supply.stats();
        int columns = Math.min(4, Math.max(1, width / BLOCK));
        float cardWidth = (width - MARGIN * (columns + 1)) / (float) columns;
        int attention = supply.needingAttention();
        Component situation = attention == 0
                ? Component.translatable("monitor.tccolonybridge.supply.all_good")
                : Component.translatable("monitor.tccolonybridge.supply.attention", attention);
        MonitorPanels.textCard(c, MARGIN, top, cardWidth, Component.translatable("monitor.tccolonybridge.supply.situation"),
                situation, attention == 0 ? UiColors.SUCCESS : UiColors.WARNING);
        if (columns >= 2) {
            MonitorPanels.metric(c, MARGIN * 2 + cardWidth, top, cardWidth,
                    Component.translatable("monitor.tccolonybridge.supply.from_network", stats.windowHours()),
                    anim.value(SERIES_METRICS, stats.restockedWindow()));
        }
        if (columns >= 3) {
            MonitorPanels.metric(c, MARGIN * 3 + cardWidth * 2, top, cardWidth,
                    Component.translatable("monitor.tccolonybridge.supply.to_network", stats.windowHours()),
                    anim.value(SERIES_METRICS + 1, stats.returnedWindow()));
        }
        if (columns >= 4) {
            MonitorPanels.textCard(c, MARGIN * 4 + cardWidth * 3, top, cardWidth,
                    Component.translatable("monitor.tccolonybridge.supply.last_activity"),
                    lastActivity(supply.minutesSinceMove()), UiColors.TEXT);
        }
    }

    /** "agora", "há 5 min", "há 2 h" ou "—" (nada desde que o mundo carregou). */
    private static Component lastActivity(long minutes) {
        if (minutes < 0) {
            return Component.literal("—");
        }
        if (minutes == 0) {
            return Component.translatable("monitor.tccolonybridge.supply.now");
        }
        return minutes < 60 ? Component.translatable("monitor.tccolonybridge.supply.minutes_ago", minutes)
                : Component.translatable("monitor.tccolonybridge.supply.hours_ago", minutes / 60);
    }

    // ---------------------------------------------------------------- lista

    /** Item da lista: título de seção ({@code line == null}) ou uma linha. */
    private record Entry(boolean keep, StockLine line) {
        float height() {
            return line == null ? SECTION_HEIGHT : ROW_HEIGHT;
        }
    }

    /** Uma ou duas colunas; a página é a mesma para as duas (a maior define quantas páginas há). */
    private static void lists(MonitorCanvas c, List<StockLine> lines, IntUnaryOperator pageFor,
                              float x, float y, float width, float height) {
        if (lines.isEmpty()) {
            c.textCentered(Component.translatable("monitor.tccolonybridge.supply.empty"), x + width / 2f,
                    y + height / 2f - 4, UiColors.TEXT_MUTED, 0.8f, width, 2);
            return;
        }
        float listHeight = height - FOOTER;
        List<List<List<Entry>>> columns = new ArrayList<>();
        if (width >= TWO_COLUMNS_FROM) {
            columns.add(paginate(section(lines, true), listHeight));
            columns.add(paginate(section(lines, false), listHeight));
        } else {
            List<Entry> both = section(lines, true);
            both.addAll(section(lines, false));
            columns.add(paginate(both, listHeight));
        }
        int pages = 1;
        for (List<List<Entry>> column : columns) {
            pages = Math.max(pages, column.size());
        }
        int page = pageFor.applyAsInt(pages);
        float columnWidth = (width - GAP * (columns.size() - 1)) / columns.size();
        for (int i = 0; i < columns.size(); i++) {
            List<List<Entry>> column = columns.get(i);
            if (page < column.size()) {
                drawPage(c, column.get(page), x + i * (columnWidth + GAP), y, columnWidth);
            }
        }
        if (pages > 1) {
            c.textCentered(Component.literal("<  " + (page + 1) + "/" + pages + "  >"), x + width / 2f,
                    y + height - FOOTER + 2, UiColors.TEXT_MUTED, 0.7f, width / 2f, 2);
        }
    }

    /** Título + linhas de um tipo; vazio se não há linhas desse tipo. */
    private static List<Entry> section(List<StockLine> lines, boolean keep) {
        List<Entry> entries = new ArrayList<>();
        for (StockLine line : lines) {
            if (line.keep() == keep) {
                entries.add(new Entry(keep, line));
            }
        }
        if (!entries.isEmpty()) {
            entries.add(0, new Entry(keep, null));
        }
        return entries;
    }

    /** Divide pela altura disponível; uma página que começa no meio de uma seção repete o título dela. */
    private static List<List<Entry>> paginate(List<Entry> entries, float height) {
        List<List<Entry>> pages = new ArrayList<>();
        List<Entry> page = new ArrayList<>();
        float used = 0;
        for (Entry entry : entries) {
            if (!page.isEmpty() && used + entry.height() > height) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
            }
            if (page.isEmpty() && entry.line() != null) {
                page.add(new Entry(entry.keep(), null));
                used += SECTION_HEIGHT;
            }
            page.add(entry);
            used += entry.height();
        }
        if (!page.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

    private static void drawPage(MonitorCanvas c, List<Entry> page, float x, float y, float width) {
        float rowY = y;
        for (Entry entry : page) {
            if (entry.line() == null) {
                String key = entry.keep() ? "monitor.tccolonybridge.supply.section_keep"
                        : "monitor.tccolonybridge.supply.section_surplus";
                c.textFitted(Component.translatable(key), x + 1, rowY + 2, UiColors.ACCENT, 0.65f, width - 2, 2);
            } else {
                row(c, entry.line(), x, rowY, width);
            }
            rowY += entry.height();
        }
    }

    /**
     * Linha: faixa à esquerda e situação na cor da gravidade; embaixo do nome, armazém × meta (ou limite)
     * e a quantidade na rede ME; barra = armazém em relação à meta/limite.
     */
    private static void row(MonitorCanvas c, StockLine line, float x, float y, float width) {
        int color = colorOf(line.status().severity());
        c.fill(x, y, x + width, y + ROW_HEIGHT - 2, UiColors.PANEL, 1);
        c.fill(x, y, x + 1.5f, y + ROW_HEIGHT - 2, color, 2);
        c.item(MonitorRequestList.icon(line.item()), x + 3, y + 3, 14, 2);
        float textX = x + 20;
        float textWidth = width - 22;

        Component status = Component.translatable(line.status().translationKey());
        float statusWidth = Math.min(c.width(status) * 0.55f, textWidth * 0.5f);
        c.text(status, x + width - 2 - statusWidth, y + 3, color, statusWidth / Math.max(1, c.width(status)), 2);
        c.textFitted(line.item().getDescription(), textX, y + 3, UiColors.TEXT, 0.6f,
                textWidth - statusWidth - 3, 2);

        Component amounts = Component.translatable(line.keep() ? "monitor.tccolonybridge.supply.warehouse_goal"
                : "monitor.tccolonybridge.supply.warehouse_limit",
                UiFormat.compact(line.warehouse()), UiFormat.compact(line.target()));
        Component network = Component.translatable("monitor.tccolonybridge.supply.network",
                UiFormat.compact(line.network()));
        float networkWidth = Math.min(c.width(network) * 0.5f, textWidth * 0.45f);
        c.text(network, x + width - 2 - networkWidth, y + 10.5f, UiColors.HIGHLIGHT,
                networkWidth / Math.max(1, c.width(network)), 2);
        c.textFitted(amounts, textX, y + 10.5f, UiColors.TEXT_MUTED, 0.5f, textWidth - networkWidth - 3, 2);

        float barY = y + 17.5f;
        float fraction = line.target() <= 0 ? 1f : Math.min(1f, (float) line.warehouse() / line.target());
        c.fill(textX, barY, textX + textWidth, barY + 2.5f, UiColors.BORDER, 2);
        c.fill(textX, barY, textX + textWidth * fraction, barY + 2.5f, color, 3);
    }

    private static int colorOf(SupplyLineStatus.Severity severity) {
        return switch (severity) {
            case OK -> UiColors.SUCCESS;
            case ACTIVE -> UiColors.HIGHLIGHT;
            case WARNING -> UiColors.WARNING;
            case PROBLEM -> UiColors.DANGER;
            case NEUTRAL -> UiColors.TEXT_MUTED;
        };
    }
}
