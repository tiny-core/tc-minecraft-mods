package org.tinycore.core.grid;

/**
 * Altura da grade de itens dos terminais TC, escolhida pelo jogador num botão da barra lateral (como o botão
 * "estilo do terminal" do AE2). Vale para todos os terminais TC e fica salva na config de cliente do core
 * ({@code TcCoreClientConfig}).
 * <p>
 * Regra pura (sem tela), para ser testada: {@link #rows} diz quantas linhas a grade tem, dado o que cabe na
 * janela do jogo. Quem desenha é a tela de cada mod.
 */
public enum GridHeight {
    /** 5 linhas (padrão). */
    SMALL(5),
    /** 8 linhas. */
    MEDIUM(8),
    /** O que couber na janela do jogo. */
    TALL(Integer.MAX_VALUE);

    private final int wantedRows;

    GridHeight(int wantedRows) {
        this.wantedRows = wantedRows;
    }

    public GridHeight next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Linhas pedidas por este modo; {@link Integer#MAX_VALUE} em {@link #TALL}. */
    public int wantedRows() {
        return wantedRows;
    }

    /**
     * Linhas da grade: as do modo, limitadas ao que cabe na janela e a {@code [minRows, maxRows]}.
     *
     * @param minRows   menor grade que a tela sabe desenhar (cabe sempre, mesmo com a janela pequena)
     * @param maxRows   teto da tela
     * @param freeSpace pixels livres na janela do jogo além da tela com {@code minRows} linhas (pode ser negativo)
     * @param rowHeight altura de uma linha em pixels (18 nas grades TC)
     */
    public int rows(int minRows, int maxRows, int freeSpace, int rowHeight) {
        int fit = minRows + Math.max(0, freeSpace) / rowHeight;
        return Math.max(minRows, Math.min(maxRows, Math.min(wantedRows, fit)));
    }
}
