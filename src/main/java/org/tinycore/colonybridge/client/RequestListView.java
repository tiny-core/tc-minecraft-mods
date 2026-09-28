package org.tinycore.colonybridge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.logic.RequestLine;

import java.util.List;

/**
 * Lista rolável de pedidos da aba "Pedidos": ícone, descrição, quantidade e resultado colorido,
 * com tooltip do texto completo. Separada da {@link ColonyBridgeScreen} para a tela só cuidar
 * de moldura, abas e botões.
 */
final class RequestListView {

    static final int ROW_HEIGHT = 22;
    static final int VISIBLE_ROWS = 6;

    private final Font font;
    private int scroll;
    /** Posição da última renderização (usada pelo tooltip e pelo scroll). */
    private int x;
    private int y;
    private int width;

    RequestListView(Font font) {
        this.font = font;
    }

    void render(GuiGraphics g, List<RequestLine> lines, int x, int y, int width, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.width = width;
        scroll = Math.min(scroll, maxScroll(lines.size()));
        if (lines.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.tccolonybridge.no_requests"),
                    x + width / 2, y + ROW_HEIGHT * VISIBLE_ROWS / 2 - 4, UiColors.TEXT_MUTED);
            return;
        }
        int rowWidth = width - 4; // espaço da barra de rolagem
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < lines.size(); i++) {
            renderRow(g, lines.get(scroll + i), y + i * ROW_HEIGHT, rowWidth, mouseX, mouseY);
        }
        renderScrollbar(g, lines.size(), x + width - 2);
    }

    private void renderRow(GuiGraphics g, RequestLine line, int rowY, int rowWidth, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + rowWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
        g.fill(x, rowY, x + rowWidth, rowY + ROW_HEIGHT - 2, hovered ? UiColors.PANEL_HOVER : UiColors.PANEL);
        g.renderItem(line.icon(), x + 2, rowY + 2);

        int textX = x + 22;
        String count = "x" + line.count();
        g.drawString(font, count, x + rowWidth - 2 - font.width(count), rowY + 2, UiColors.TEXT_MUTED, false);
        int labelWidth = rowWidth - 24 - font.width(count) - 4;
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(line.label(), labelWidth)),
                textX, rowY + 2, UiColors.TEXT, false);
        g.drawString(font, Component.translatable(line.outcome().translationKey()),
                textX, rowY + 11, StatusColors.of(line.outcome()), false);
    }

    private void renderScrollbar(GuiGraphics g, int total, int barX) {
        if (total <= VISIBLE_ROWS) {
            return;
        }
        int trackHeight = ROW_HEIGHT * VISIBLE_ROWS - 2;
        int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / total);
        int thumbY = y + (trackHeight - thumbHeight) * scroll / maxScroll(total);
        g.fill(barX, y, barX + 2, y + trackHeight, UiColors.BORDER);
        g.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, UiColors.ACCENT);
    }

    /** Descrição completa ao passar o mouse sobre uma linha (o texto na linha pode estar cortado). */
    void renderTooltip(GuiGraphics g, List<RequestLine> lines, int mouseX, int mouseY) {
        int relY = mouseY - y;
        if (mouseX < x || mouseX >= x + width - 4 || relY < 0 || relY / ROW_HEIGHT >= VISIBLE_ROWS) {
            return;
        }
        int index = scroll + relY / ROW_HEIGHT;
        if (index >= lines.size()) {
            return;
        }
        RequestLine line = lines.get(index);
        g.renderComponentTooltip(font, List.of(line.label(),
                Component.translatable("gui.tccolonybridge.amount", line.count()),
                Component.translatable(line.outcome().translationKey())), mouseX, mouseY);
    }

    /** @param direction positivo = rolar para cima */
    void scroll(double direction, int total) {
        scroll = Math.max(0, Math.min(maxScroll(total), scroll - (int) Math.signum(direction)));
    }

    private static int maxScroll(int total) {
        return Math.max(0, total - VISIBLE_ROWS);
    }
}
