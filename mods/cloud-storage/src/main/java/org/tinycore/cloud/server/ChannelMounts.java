package org.tinycore.cloud.server;

import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Um canal só pode estar montado na rede AE2 por <b>um</b> TC Cloud Link por servidor (plano §4): dois Links no
 * mesmo canal fariam o AE2 contar os itens em dobro e errar os cálculos do autocraft. O primeiro que pede fica
 * com o canal até ser removido do mundo. Vive dentro do {@link CloudService}, então zera a cada servidor.
 */
public final class ChannelMounts {

    private final Map<UUID, GlobalPos> holders = new HashMap<>();

    /** Tenta ficar com o canal; true se conseguiu (ou já era deste Link). */
    public boolean claim(@NotNull UUID channel, @NotNull GlobalPos link) {
        GlobalPos current = holders.putIfAbsent(channel, link);
        return current == null || current.equals(link);
    }

    /** Solta todos os canais deste Link. */
    public void release(@NotNull GlobalPos link) {
        holders.values().removeIf(link::equals);
    }

    /** Onde o canal está montado, ou {@code null}. */
    public @Nullable GlobalPos holder(@NotNull UUID channel) {
        return holders.get(channel);
    }
}
