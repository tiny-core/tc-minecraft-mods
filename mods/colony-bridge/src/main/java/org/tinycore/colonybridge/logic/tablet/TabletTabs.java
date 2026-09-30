package org.tinycore.colonybridge.logic.tablet;

import org.jetbrains.annotations.Nullable;

/**
 * Regras puras das abas do tablet (testadas sem o jogo). Quais abas estão disponíveis viaja como um número
 * ("máscara de bits": o bit {@code n} ligado = a aba de ordinal {@code n} está disponível), que cabe num
 * único inteiro do pacote.
 */
public final class TabletTabs {

    /** Ordem de preferência ao abrir o tablet sem aba escolhida: o Terminal é a tela principal. */
    private static final TabletTab[] OPEN_ORDER = {TabletTab.TERMINAL, TabletTab.BRIDGE, TabletTab.SUPPLY};

    private TabletTabs() {}

    /** Máscara com esta aba ligada (ou desligada). */
    public static int with(int mask, TabletTab tab, boolean available) {
        int bit = 1 << tab.ordinal();
        return available ? mask | bit : mask & ~bit;
    }

    /** true se a aba está na máscara <b>e</b> já foi implementada. */
    public static boolean isAvailable(int mask, TabletTab tab) {
        return tab.implemented() && (mask & (1 << tab.ordinal())) != 0;
    }

    /**
     * Aba a abrir: a pedida, se disponível; sem pedido, a primeira disponível na ordem
     * Terminal → Ponte → Abastecedor.
     *
     * @return null se nada serve (aba pedida indisponível, ou nenhum bloco disponível)
     */
    public static @Nullable TabletTab choose(int mask, @Nullable TabletTab wanted) {
        if (wanted != null) {
            return isAvailable(mask, wanted) ? wanted : null;
        }
        for (TabletTab tab : OPEN_ORDER) {
            if (isAvailable(mask, tab)) {
                return tab;
            }
        }
        return null;
    }

    /**
     * FE a descontar da bateria num passo (o tablet gasta a cada {@code ticks}, não a cada tick, para não
     * trocar o item na mão — e mandar um pacote — todo tick).
     */
    public static int drainStep(int perTick, int ticks) {
        long value = (long) Math.max(0, perTick) * Math.max(0, ticks);
        return (int) Math.min(Integer.MAX_VALUE, value);
    }
}
