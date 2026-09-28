package org.tinycore.colonybridge.menu;

import java.util.AbstractList;
import java.util.List;

/**
 * Junta duas listas de tamanho fixo numa só, sem copiar: ler ou gravar a posição {@code i} vai direto
 * para a lista de origem. Serve para um menu ter dois grupos de ghost slots (ex.: filtro + itens preferidos
 * da ponte) guardados em listas separadas no bloco.
 * <p>
 * {@code AbstractList} é a base da biblioteca padrão para listas próprias: basta implementar
 * {@code get}, {@code set} e {@code size} (≈ implementar {@code IList<T>} em C#, com o resto pronto).
 */
public final class JoinedList<T> extends AbstractList<T> {

    private final List<T> first;
    private final List<T> second;

    public JoinedList(List<T> first, List<T> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public T get(int index) {
        return index < first.size() ? first.get(index) : second.get(index - first.size());
    }

    @Override
    public T set(int index, T value) {
        return index < first.size() ? first.set(index, value) : second.set(index - first.size(), value);
    }

    @Override
    public int size() {
        return first.size() + second.size();
    }
}
