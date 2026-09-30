package org.tinycore.colonybridge.logic.target;

import java.util.List;

/**
 * Edições de uma lista de linhas vindas da tela: adicionar, trocar e remover, sempre com limites.
 * Genérica ({@code <T>}) para ser testada sem itens; quem usa é o {@link TargetList}.
 * <p>
 * O índice e o tamanho vêm do cliente (pacote), então tudo é conferido: índice fora da lista ou lista
 * cheia simplesmente não muda nada e devolve false.
 */
public final class ListEdits {

    private ListEdits() {}

    /** Adiciona no fim, se couber em {@code maxLines}. */
    public static <T> boolean add(List<T> lines, T line, int maxLines) {
        if (lines.size() >= maxLines) {
            return false;
        }
        lines.add(line);
        return true;
    }

    /** Troca a linha {@code index}, se ela existe. */
    public static <T> boolean set(List<T> lines, int index, T line) {
        if (!inRange(lines, index)) {
            return false;
        }
        lines.set(index, line);
        return true;
    }

    /** Remove a linha {@code index}, se ela existe (as de baixo sobem uma posição). */
    public static <T> boolean remove(List<T> lines, int index) {
        if (!inRange(lines, index)) {
            return false;
        }
        lines.remove(index);
        return true;
    }

    /**
     * Corta o que passar de {@code maxLines} (ex.: o admin baixou o limite na config). As linhas do fim
     * são descartadas, as primeiras ficam.
     */
    public static <T> boolean trim(List<T> lines, int maxLines) {
        if (lines.size() <= maxLines) {
            return false;
        }
        lines.subList(Math.max(0, maxLines), lines.size()).clear();
        return true;
    }

    private static boolean inRange(List<?> lines, int index) {
        return index >= 0 && index < lines.size();
    }
}
