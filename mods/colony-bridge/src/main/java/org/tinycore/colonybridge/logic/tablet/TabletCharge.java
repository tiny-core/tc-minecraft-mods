package org.tinycore.colonybridge.logic.tablet;

/**
 * Regras puras da bateria do tablet (só números, testadas sem o jogo): quanto carregar num passo e o tamanho
 * da barra de energia no ícone do item.
 */
public final class TabletCharge {

    /** Largura máxima da barra de "durabilidade" que o Minecraft desenha sob o ícone. */
    public static final int BAR_WIDTH = 13;

    private TabletCharge() {}

    /**
     * FE a pôr na bateria num passo de carga.
     *
     * @param stored   energia atual
     * @param capacity capacidade
     * @param perTick  taxa de carga (config)
     * @param ticks    ticks desde o último passo
     * @return 0 se cheia; nunca passa do espaço livre (sem estouro de {@code int})
     */
    public static int step(int stored, int capacity, int perTick, int ticks) {
        long free = (long) capacity - Math.max(0, stored);
        long wanted = (long) Math.max(0, perTick) * Math.max(0, ticks);
        return (int) Math.max(0, Math.min(free, wanted));
    }

    /** Largura da barra (0 a {@link #BAR_WIDTH}) para a energia guardada. */
    public static int barWidth(int stored, int capacity) {
        if (capacity <= 0) {
            return 0;
        }
        double fraction = Math.max(0, Math.min(1, (double) stored / capacity));
        return (int) Math.round(fraction * BAR_WIDTH);
    }
}
