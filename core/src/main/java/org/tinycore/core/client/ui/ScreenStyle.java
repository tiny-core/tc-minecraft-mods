package org.tinycore.core.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Estilo das telas (interfaces) do mod: <b>estrutura</b> inspirada nos terminais do AE2 (janela com relevo,
 * slots e painéis afundados, barra lateral de botões com ícone, seções com título) e <b>cores da marca</b>
 * TCMine ({@link UiColors}: fundo escuro, laranja e ciano). Desenhado só com retângulos, sem texturas.
 * <p>
 * Também resolve texto que não cabe: {@link #drawFitted} corta com "…" em vez de invadir o vizinho
 * (a fonte do ATM10 é mais larga que a padrão, então nada aqui assume largura fixa de texto).
 */
public final class ScreenStyle {

    /** Contorno externo da janela e dos botões. */
    public static final int FRAME = 0xFF0E1015;
    /** Fio claro do relevo (em cima/esquerda da janela; embaixo/direita dos afundados). */
    public static final int LIGHT = UiColors.BORDER;
    /** Sombra do relevo (embaixo/direita da janela). */
    public static final int SHADE = 0xFF111318;
    public static final int WINDOW = 0xFF1B1E26;
    public static final int PANEL = UiColors.PANEL;
    public static final int HOVER = UiColors.PANEL_HOVER;
    /** Fundo dos slots: mais escuro que a janela, como no AE2. */
    public static final int SLOT = 0xFF15171D;
    /** Borda escura dos afundados (em cima/esquerda). */
    public static final int EDGE = 0xFF0B0C10;

    public static final int TITLE = UiColors.ACCENT;
    public static final int TEXT = UiColors.TEXT;
    public static final int TEXT_MUTED = UiColors.TEXT_MUTED;
    public static final int ACCENT = UiColors.ACCENT;
    public static final int INFO = UiColors.HIGHLIGHT;
    public static final int SUCCESS = UiColors.SUCCESS;
    public static final int WARNING = UiColors.WARNING;
    public static final int DANGER = UiColors.DANGER;

    private ScreenStyle() {}

    /** Janela: contorno escuro, relevo (claro em cima/esquerda, sombra embaixo/direita), fundo e faixa laranja. */
    public static void window(GuiGraphics g, int x, int y, int width, int height) {
        box(g, x, y, width, height);
        g.fill(x + 2, y + 2, x + width - 2, y + 3, ACCENT);
    }

    /** Caixa com relevo sem a faixa laranja (ex.: fundo da barra lateral). */
    public static void box(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, FRAME);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, LIGHT);
        g.fill(x + 2, y + 2, x + width - 1, y + height - 1, SHADE);
        g.fill(x + 2, y + 2, x + width - 2, y + height - 2, WINDOW);
    }

    /** Slot de 18×18 afundado, com o canto de cima/esquerda do item em ({@code x}+1, {@code y}+1). */
    public static void slot(GuiGraphics g, int x, int y) {
        inset(g, x, y, 18, 18, SLOT);
    }

    /** Painel afundado (listas, cartões): borda escura em cima/esquerda, clara embaixo/direita. */
    public static void inset(GuiGraphics g, int x, int y, int width, int height, int fill) {
        g.fill(x, y, x + width, y + height, LIGHT);
        g.fill(x, y, x + width - 1, y + height - 1, EDGE);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    /**
     * Barra de rolagem vertical (trilho afundado + alça).
     *
     * @param first   primeira linha visível
     * @param visible linhas que cabem
     * @param total   total de linhas
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int height, int first, int visible, int total) {
        inset(g, x, y, 6, height, SLOT);
        if (total <= visible) {
            return;
        }
        int track = height - 2;
        int thumb = Math.max(8, track * visible / total);
        int top = y + 1 + (track - thumb) * first / Math.max(1, total - visible);
        g.fill(x + 1, top, x + 5, top + thumb, ACCENT);
    }

    /** Pequeno quadrado de estado com contorno. */
    public static void statusDot(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 7, y + 7, FRAME);
        g.fill(x + 1, y + 1, x + 6, y + 6, color);
    }

    /**
     * Desenha o texto sem sombra; se passar de {@code maxWidth}, corta e termina com "…".
     *
     * @return largura efetivamente desenhada
     */
    public static int drawFitted(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        FormattedCharSequence line = fit(font, text, maxWidth);
        g.drawString(font, line, x, y, color, false);
        return font.width(line);
    }

    /** Mesmo que {@link #drawFitted}, alinhado à direita em {@code right}. */
    public static int drawFittedRight(GuiGraphics g, Font font, Component text, int right, int y, int maxWidth,
                                      int color) {
        FormattedCharSequence line = fit(font, text, maxWidth);
        int width = font.width(line);
        g.drawString(font, line, right - width, y, color, false);
        return width;
    }

    /** Texto cortado para caber em {@code maxWidth}, com "…" no fim quando foi cortado. */
    public static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        String ellipsis = "…";
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ellipsis)));
    }
}
