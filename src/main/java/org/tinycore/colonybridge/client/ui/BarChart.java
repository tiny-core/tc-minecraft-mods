package org.tinycore.colonybridge.client.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Gráfico de barras simples (componente do design system): barras proporcionais ao maior valor,
 * a última barra destacada como "agora". Sem estado — pode ser desenhado em telas e, na Fase 5,
 * nos monitores.
 */
public final class BarChart {

    private BarChart() {}

    /**
     * @param values valores do mais antigo ao mais recente; vazio desenha só o fundo
     */
    public static void render(GuiGraphics g, int x, int y, int width, int height, List<Integer> values) {
        g.fill(x, y, x + width, y + height, UiColors.PANEL);
        g.fill(x, y + height - 1, x + width, y + height, UiColors.BORDER); // linha de base
        if (values.isEmpty()) {
            return;
        }
        int max = 1;
        for (int value : values) {
            max = Math.max(max, value);
        }
        int count = values.size();
        int slot = Math.max(1, (width - 2) / count);
        int barWidth = Math.max(1, slot - 1);
        int usableHeight = height - 3;
        int startX = x + 1 + (width - 2 - slot * count) / 2; // centraliza as barras
        for (int i = 0; i < count; i++) {
            int value = values.get(i);
            if (value <= 0) {
                continue;
            }
            int barHeight = Math.max(1, (int) ((long) value * usableHeight / max));
            int bx = startX + i * slot;
            int color = i == count - 1 ? UiColors.ACCENT : UiColors.HIGHLIGHT;
            g.fill(bx, y + height - 1 - barHeight, bx + barWidth, y + height - 1, color);
        }
    }
}
