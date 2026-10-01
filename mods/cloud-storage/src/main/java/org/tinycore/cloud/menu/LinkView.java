package org.tinycore.cloud.menu;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lado do cliente da tela do TC Cloud Link: o que o servidor mandou (cabeçalho e itens). Só dados, nenhuma classe
 * de tela, por isso mora no pacote do menu. {@link #version()} aumenta a cada pacote, e a grade só refaz a lista
 * quando ele muda.
 */
public final class LinkView {

    private final Map<String, LinkEntry> entries = new LinkedHashMap<>();
    private List<LinkEntry> list = List.of();
    private LinkHeader header = LinkHeader.EMPTY;
    private int version;

    public void apply(boolean reset, @NotNull LinkHeader header, @NotNull List<LinkEntry> changes) {
        if (reset) entries.clear();
        for (LinkEntry e : changes) {
            if (e.amount() <= 0) entries.remove(e.fingerprint());
            else entries.put(e.fingerprint(), e);
        }
        this.header = header;
        this.list = new ArrayList<>(entries.values());
        version++;
    }

    public int version() {
        return version;
    }

    public @NotNull List<LinkEntry> entries() {
        return list;
    }

    public @NotNull LinkHeader header() {
        return header;
    }
}
