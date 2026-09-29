package org.tinycore.colonybridge.multiblock;

/**
 * Geometria de um grupo de monitores na parede, em coordenadas da própria tela: {@code u} cresce para a
 * direita de quem olha e {@code v} para cima. É a regra pura do {@link MonitorFormation} (sem mundo nem
 * blocos), para poder ser testada sem o jogo.
 * <p>
 * Uso: {@link #add} para cada bloco do grupo e depois {@link #isValid}. O canto de menor {@code u} e
 * {@code v} ({@link #minU}, {@link #minV}) é o inferior esquerdo, onde fica o mestre.
 */
final class MonitorShape {

    private int minU = Integer.MAX_VALUE;
    private int maxU = Integer.MIN_VALUE;
    private int minV = Integer.MAX_VALUE;
    private int maxV = Integer.MIN_VALUE;
    private int count;

    /** Registra um bloco do grupo (cada posição deve ser adicionada uma vez só). */
    void add(int u, int v) {
        minU = Math.min(minU, u);
        maxU = Math.max(maxU, u);
        minV = Math.min(minV, v);
        maxV = Math.max(maxV, v);
        count++;
    }

    int minU() {
        return minU;
    }

    int minV() {
        return minV;
    }

    int width() {
        return count == 0 ? 0 : maxU - minU + 1;
    }

    int height() {
        return count == 0 ? 0 : maxV - minV + 1;
    }

    /**
     * true se o grupo é um retângulo <b>completo</b> (sem buracos: número de blocos = largura × altura)
     * dentro do tamanho máximo da config.
     */
    boolean isValid(int maxWidth, int maxHeight) {
        return count > 0
                && count == width() * height()
                && width() <= maxWidth
                && height() <= maxHeight;
    }
}
