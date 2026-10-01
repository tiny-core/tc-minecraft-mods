package org.tinycore.cloud.item;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Pergunta "este item tem itens dentro?" (regra 4 do plano §6). Cada fonte de conteúdo é um probe: o vanilla
 * e o NeoForge ficam em {@link VanillaContentProbe}; o AE2 em {@code integration/ae2/Ae2CellProbe}. Assim o
 * {@link TransferGuard} não importa nada de mod externo.
 */
@FunctionalInterface
public interface ContentProbe {

    boolean hasContents(@NotNull ItemStack stack);
}
