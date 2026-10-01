package org.tinycore.cloud.server;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Avisa quem depende da nuvem de um jogador (o TC Cloud Link no mundo, para remontar o armazenamento no AE2)
 * quando a situação dele muda: lease chegou, saiu, ficou somente leitura. Quem se registra precisa se
 * remover ao sair do mundo (chunk descarregado, bloco quebrado). Só na thread do servidor.
 */
public final class CloudListeners {

    private final Map<UUID, List<Runnable>> byPlayer = new HashMap<>();

    public void add(@NotNull UUID player, @NotNull Runnable listener) {
        byPlayer.computeIfAbsent(player, k -> new ArrayList<>()).add(listener);
    }

    public void remove(@NotNull UUID player, @NotNull Runnable listener) {
        List<Runnable> list = byPlayer.get(player);
        if (list == null) return;
        list.remove(listener);
        if (list.isEmpty()) byPlayer.remove(player);
    }

    void fire(@NotNull UUID player) {
        List<Runnable> list = byPlayer.get(player);
        if (list == null) return;
        for (Runnable listener : List.copyOf(list)) listener.run(); // cópia: o aviso pode remover listeners
    }
}
