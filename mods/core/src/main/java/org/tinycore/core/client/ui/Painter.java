package org.tinycore.core.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Superfície onde os componentes do design system desenham: a interface ({@code GuiGraphics}) ou a
 * tela do monitor no mundo. Assim o mesmo componente (ex.: {@link BarChart}) serve aos dois.
 * <p>
 * {@code depth}: ordem de sobreposição (0 = fundo). A interface ignora; o monitor usa para evitar
 * que camadas no mesmo plano "pisquem".
 */
@FunctionalInterface
public interface Painter {

    void fill(float x0, float y0, float x1, float y1, int color, int depth);

    /** Adaptador para telas de interface. */
    static Painter of(GuiGraphics graphics) {
        return (x0, y0, x1, y1, color, depth) ->
                graphics.fill((int) x0, (int) y0, (int) x1, (int) y1, color);
    }
}
