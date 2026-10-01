package org.tinycore.cloud.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.server.CloudInventory;

import java.util.UUID;

/**
 * Regras das retiradas e depósitos pela tela do TC Cloud Link, no servidor. Regra anti-duplicação do workspace:
 * calcula quanto cabe ANTES de tirar da nuvem; se mesmo assim sobrar algo, volta para a nuvem; se nem isso
 * der, cai aos pés do jogador. Nunca some, nunca duplica.
 */
final class CloudLinkActions {

    private CloudLinkActions() {}

    /** Tira até {@code want} unidades do item para o inventário do jogador. */
    static void take(@NotNull ServerPlayer player, @NotNull CloudInventory inventory, @NotNull UUID channel,
                     @NotNull String fingerprint, int want) {
        ItemStack prototype = inventory.prototype(fingerprint);
        if (prototype == null) return; // incompatível ou bloqueado neste servidor
        int fit = room(player.getInventory(), prototype, Math.min(want, prototype.getMaxStackSize()));
        if (fit <= 0) return;
        long taken = inventory.extract(player.getUUID(), channel, fingerprint, fit, false);
        if (taken <= 0) return;
        ItemStack given = prototype.copyWithCount((int) taken);
        player.getInventory().add(given);
        if (given.isEmpty()) return;
        // Não deveria acontecer (calculamos o espaço antes), mas se acontecer, nada se perde.
        long back = inventory.insert(player.getUUID(), channel, given, given.getCount(), false).accepted();
        given.shrink((int) back);
        if (!given.isEmpty()) {
            TcCloud.LOG.warn("Nuvem: {} x{} não coube nem voltou; derrubado perto de {}.", given, given.getCount(),
                    player.getGameProfile().getName());
            player.drop(given, false);
        }
    }

    /** Quantas unidades de {@code item} cabem no inventário principal (sem armadura), até {@code max}. */
    static int room(@NotNull Inventory inventory, @NotNull ItemStack item, int max) {
        int room = 0;
        for (int slot = 0; slot < inventory.items.size() && room < max; slot++) {
            ItemStack there = inventory.items.get(slot);
            if (there.isEmpty()) {
                room += item.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(there, item)) {
                room += Math.max(0, there.getMaxStackSize() - there.getCount());
            }
        }
        return Math.min(room, max);
    }
}
