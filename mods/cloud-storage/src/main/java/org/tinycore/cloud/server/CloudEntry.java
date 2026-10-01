package org.tinycore.cloud.server;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.item.ItemCompatibility;

/**
 * Um item de um canal, visto deste servidor (para a tela e o AE2).
 *
 * @param prototype o item com quantidade 1; {@code null} quando não decodifica aqui
 */
public record CloudEntry(@NotNull String fingerprint, @Nullable ItemStack prototype, @NotNull String itemId,
                         @NotNull String name, long amount, @NotNull ItemCompatibility status) {}
