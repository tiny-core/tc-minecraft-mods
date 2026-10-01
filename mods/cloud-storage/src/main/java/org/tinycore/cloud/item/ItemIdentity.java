package org.tinycore.cloud.item;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * "Qual item é este", ignorando a quantidade: o tipo + os componentes que diferem do padrão. Serve de chave
 * de cache ({@code ItemStack} não implementa {@code equals}), para não recodificar o mesmo item a cada
 * chamada do AE2.
 */
public record ItemIdentity(@NotNull Item item, @NotNull DataComponentPatch patch) {

    public static @NotNull ItemIdentity of(@NotNull ItemStack stack) {
        return new ItemIdentity(stack.getItem(), stack.getComponentsPatch());
    }
}
