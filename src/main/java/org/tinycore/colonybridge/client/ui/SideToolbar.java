package org.tinycore.colonybridge.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Barra lateral de botões com ícone, colada à esquerda da janela, como nos terminais do AE2. Guarda os
 * botões, empilha os visíveis de cima para baixo ({@link #layout}) e desenha a caixa atrás deles.
 * <p>
 * Fica fora da área da janela, então a tela precisa informar {@link #area()} a mods como o JEI, para
 * eles não desenharem por cima.
 */
public final class SideToolbar {

    private static final int STEP = IconButton.SIZE + 2;
    private static final int PADDING = 3;
    /** Largura da caixa: botão + bordas; encosta 1 px na janela para parecer presa a ela. */
    private static final int WIDTH = IconButton.SIZE + PADDING * 2;

    private final List<IconButton> buttons = new ArrayList<>();
    private int left;
    private int top;
    private int visibleCount;

    public IconButton add(IconButton button) {
        buttons.add(button);
        return button;
    }

    /** Reposiciona os botões visíveis a partir do canto da janela. Chamar após mudar a visibilidade. */
    public void layout(int windowLeft, int windowTop) {
        left = windowLeft - WIDTH + 1;
        top = windowTop + 4;
        visibleCount = 0;
        for (IconButton button : buttons) {
            if (button.visible) {
                button.setPosition(left + PADDING, top + PADDING + visibleCount * STEP);
                visibleCount++;
            }
        }
    }

    /** Caixa atrás dos botões (chamar no {@code renderBg}, antes dos widgets). */
    public void render(GuiGraphics g) {
        if (visibleCount > 0) {
            ScreenStyle.box(g, left, top, WIDTH, height());
        }
    }

    /** Área ocupada, para o JEI não cobrir a barra. */
    public Rect2i area() {
        return new Rect2i(left, top, WIDTH, height());
    }

    private int height() {
        return visibleCount * STEP - 2 + PADDING * 2;
    }
}
