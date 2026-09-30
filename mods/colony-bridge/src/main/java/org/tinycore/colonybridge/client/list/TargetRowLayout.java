package org.tinycore.colonybridge.client.list;

import org.tinycore.colonybridge.logic.target.TargetListKind;

/**
 * Posições (em pixels, absolutas na tela) das partes de uma linha da {@link TargetListWidget}, calculadas uma
 * vez por {@code init()} da tela. Da esquerda para a direita:
 * <pre>
 * ▌[ícone] [ texto do alvo ........ ] [ qtd ] [∞] [x]  ▐ barra de rolagem
 * </pre>
 * A quantidade só existe nas listas com quantidade e o "∞" (tudo) só no Excedente ({@link TargetListKind}).
 * Separado do widget só para ele não virar um arquivo gigante.
 */
record TargetRowLayout(int x, int y, int width, int rows, int iconX, int textX, int textWidth, int amountX,
                       int allX, int removeX, int scrollbarX, int addX, int addY) {

    static final int ROW_HEIGHT = 20;
    static final int ICON = 18;
    static final int BUTTON = 12;
    static final int AMOUNT_WIDTH = 32;
    static final int SCROLLBAR = 6;
    private static final int GAP = 2;

    /**
     * @param y topo da primeira linha; o botão "+" fica na faixa logo acima ({@code y - 13}), à direita
     */
    static TargetRowLayout of(TargetListKind kind, int x, int y, int width, int rows) {
        int scrollbarX = x + width - SCROLLBAR;
        int removeX = scrollbarX - GAP - BUTTON;
        int allX = kind.allowsAll() ? removeX - GAP - BUTTON : removeX;
        int amountX = kind.hasAmount() ? allX - GAP - AMOUNT_WIDTH : allX;
        int iconX = x + 3;
        int textX = iconX + ICON + 3;
        int textRight = (kind.hasAmount() ? amountX : allX) - GAP;
        return new TargetRowLayout(x, y, width, rows, iconX, textX, textRight - textX, amountX, allX, removeX,
                scrollbarX, x + width - BUTTON, y - BUTTON - 1);
    }

    int rowY(int row) {
        return y + row * ROW_HEIGHT;
    }

    int height() {
        return rows * ROW_HEIGHT;
    }

    /** Linha visível sob o cursor, ou -1. */
    int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= scrollbarX || mouseY < y || mouseY >= y + height()) {
            return -1;
        }
        return (int) ((mouseY - y) / ROW_HEIGHT);
    }

    boolean isOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height();
    }

    static boolean in(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
