package org.tinycore.colonybridge.client.ui;

import java.util.List;

/**
 * Gráfico de barras simples (componente do design system): barras proporcionais ao maior valor,
 * a última barra destacada como "agora". Sem estado; desenha num {@link Painter}, então serve
 * tanto para a interface quanto para a tela do monitor.
 */
public final class BarChart {

    private BarChart() {}

    /**
     * @param values valores do mais antigo ao mais recente; vazio desenha só o fundo
     */
    public static void render(Painter g, float x, float y, float width, float height, List<Integer> values, int depth) {
        g.fill(x, y, x + width, y + height, UiColors.PANEL, depth);
        g.fill(x, y + height - 1, x + width, y + height, UiColors.BORDER, depth + 1); // linha de base
        if (values.isEmpty()) {
            return;
        }
        int max = 1;
        for (int value : values) {
            max = Math.max(max, value);
        }
        int count = values.size();
        float slot = Math.max(1, (width - 2) / count);
        float barWidth = Math.max(1, slot * 0.8f);
        float usableHeight = height - 3;
        float startX = x + 1 + (width - 2 - slot * count) / 2; // centraliza as barras
        for (int i = 0; i < count; i++) {
            int value = values.get(i);
            if (value <= 0) {
                continue;
            }
            float barHeight = Math.max(1, value * usableHeight / max);
            float bx = startX + i * slot;
            int color = i == count - 1 ? UiColors.ACCENT : UiColors.HIGHLIGHT;
            g.fill(bx, y + height - 1 - barHeight, bx + barWidth, y + height - 1, color, depth + 1);
        }
    }
}
