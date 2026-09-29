package org.tinycore.colonybridge.menu.terminal;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.logic.terminal.CountDiff;
import org.tinycore.colonybridge.logic.warehouse.WarehouseItems;
import org.tinycore.colonybridge.network.WarehouseContentsPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lado servidor da grade do Terminal do Armazém: a cada {@code terminalSyncTicks} (só com a tela aberta)
 * soma os racks e manda ao cliente apenas o que mudou desde o último envio ({@link CountDiff}).
 * <p>
 * Por que diferença e não a lista toda: os couriers mexem no armazém o tempo todo, e um armazém grande
 * tem milhares de tipos. Mandar só as mudanças mantém os pacotes pequenos. O total de tipos é limitado por
 * {@code terminalMaxTypes} para uma colônia gigante não gerar tráfego sem fim.
 */
final class WarehouseSync {

    private final ServerPlayer player;
    private final int containerId;
    private Object2LongLinkedOpenCustomHashMap<ItemStack> lastSent = WarehouseItems.newCountMap();
    private boolean needsReset = true;
    private int ticksUntilScan;

    WarehouseSync(ServerPlayer player, int containerId) {
        this.player = player;
        this.containerId = containerId;
    }

    /** Pede leitura no próximo tick (depois de uma ação do jogador, para a grade reagir na hora). */
    void scanSoon() {
        ticksUntilScan = 0;
    }

    /**
     * Chamado todo tick pelo menu; só trabalha quando o intervalo vence. Os racks vêm de um
     * {@code Supplier} (≈ {@code Func<T>} em C#) para a busca na colônia também só rodar nessa hora.
     */
    void tick(Supplier<List<IItemHandler>> racks) {
        if (--ticksUntilScan > 0) {
            return;
        }
        ticksUntilScan = Config.TERMINAL_SYNC_TICKS.get();
        Object2LongLinkedOpenCustomHashMap<ItemStack> current = limited(WarehouseItems.countAll(racks.get()));
        List<WarehouseEntry> changes = new ArrayList<>();
        CountDiff.forEachChange(lastSent, current, (item, count) -> changes.add(new WarehouseEntry(item, count)));
        if (changes.isEmpty() && !needsReset) {
            return;
        }
        send(changes);
        lastSent = current;
        needsReset = false;
    }

    /** Envia em pedaços de até {@link WarehouseContentsPayload#MAX_ENTRIES}; o primeiro leva o reset. */
    private void send(List<WarehouseEntry> changes) {
        int max = WarehouseContentsPayload.MAX_ENTRIES;
        boolean reset = needsReset;
        int from = 0;
        do {
            List<WarehouseEntry> part = List.copyOf(changes.subList(from, Math.min(changes.size(), from + max)));
            PacketDistributor.sendToPlayer(player, new WarehouseContentsPayload(containerId, reset, part));
            reset = false;
            from += max;
        } while (from < changes.size());
    }

    /** Corta os tipos além do limite da config (a ordem é a dos racks, então o corte é estável). */
    private static Object2LongLinkedOpenCustomHashMap<ItemStack> limited(Object2LongLinkedOpenCustomHashMap<ItemStack> all) {
        int max = Config.TERMINAL_MAX_TYPES.get();
        if (all.size() <= max) {
            return all;
        }
        Object2LongLinkedOpenCustomHashMap<ItemStack> cut = WarehouseItems.newCountMap();
        for (var entry : all.object2LongEntrySet()) {
            if (cut.size() >= max) {
                break;
            }
            cut.put(entry.getKey(), entry.getLongValue());
        }
        return cut;
    }
}
