package org.tinycore.colonybridge.client.ui;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.tinycore.colonybridge.block.RedstoneMode;

/**
 * Ícone do modo de redstone, igual na Ponte e no Abastecedor: pólvora = redstone ignorada,
 * pó de redstone = só com sinal, tocha de redstone (que inverte o sinal) = só sem sinal.
 * Os stacks são criados uma vez e só lidos: nada é alocado a cada frame.
 */
public final class RedstoneIcons {

    private static final ItemStack GUNPOWDER = new ItemStack(Items.GUNPOWDER);
    private static final ItemStack REDSTONE = new ItemStack(Items.REDSTONE);
    private static final ItemStack REDSTONE_TORCH = new ItemStack(Items.REDSTONE_TORCH);

    private RedstoneIcons() {}

    public static ItemStack of(RedstoneMode mode) {
        return switch (mode) {
            case IGNORED -> GUNPOWDER;
            case ACTIVE_WITH_SIGNAL -> REDSTONE;
            case ACTIVE_WITHOUT_SIGNAL -> REDSTONE_TORCH;
        };
    }
}
