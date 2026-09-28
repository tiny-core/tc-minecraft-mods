package org.tinycore.colonybridge.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Estilo das telas (interfaces) do mod, no visual dos terminais do AE2: fundo cinza-lilás claro, bordas
 * em relevo, slots e painéis "afundados" e texto escuro. Cores medidas num print do terminal do AE2 no
 * ATM10, para as telas do mod parecerem parte da mesma família.
 * <p>
 * Desenhado só com retângulos (sem as texturas do AE2): apontar para arquivos internos de outro mod
 * quebraria se eles fossem renomeados numa atualização. Os monitores no mundo continuam com
 * {@link UiColors} (tema escuro de display).
 * <p>
 * Também resolve texto que não cabe: {@link #drawFitted} corta com "…" em vez de invadir o vizinho
 * (a fonte do ATM10 é mais larga que a padrão, então nada aqui assume largura fixa de texto).
 */
public final class ScreenStyle {

    public static final int FRAME = 0xFF413F54;
    public static final int LIGHT = 0xFFF2F2F2;
    public static final int WINDOW = 0xFFCBCCD4;
    public static final int SLOT = 0xFFADB0C4;
    public static final int SHADE = 0xFF9A9FB4;
    public static final int EDGE = 0xFF696D88;

    public static final int TEXT = 0xFF403E53;
    public static final int TEXT_MUTED = 0xFF6E7189;
    /** Texto sobre botões ({@link #SHADE}): mais escuro que {@link #TEXT} para manter contraste. */
    public static final int TEXT_ON_BUTTON = 0xFF1E1D28;

    /** Cores de destaque escurecidas para ler bem sobre o fundo claro. */
    public static final int INFO = 0xFF1C6FA0;
    public static final int SUCCESS = 0xFF1F7A45;
    public static final int WARNING = 0xFFA15C07;
    public static final int DANGER = 0xFFB42323;

    private ScreenStyle() {}

    /** Janela: contorno escuro, relevo claro em cima/esquerda, sombra embaixo/direita e fundo. */
    public static void window(GuiGraphics g, int x, int y, int width, int height) {
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

    /** Pequeno quadrado de estado com contorno, legível sobre o fundo claro. */
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
