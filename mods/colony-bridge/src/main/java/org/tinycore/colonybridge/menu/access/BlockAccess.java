package org.tinycore.colonybridge.menu.access;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;

/**
 * Tela aberta clicando no bloco: vale enquanto o bloco existe e o jogador está ao alcance, a mesma regra do
 * {@code AbstractContainerMenu.stillValid} do Minecraft (que é {@code protected} e por isso repetida aqui).
 */
final class BlockAccess implements MenuAccess {

    /** Alcance padrão de interação com blocos do Minecraft 1.21 (além do alcance do braço). */
    private static final double RANGE = 4.0;

    private final Level level;
    private final BlockPos pos;
    private final Block block;

    BlockAccess(BlockEntity blockEntity) {
        this.level = Objects.requireNonNull(blockEntity.getLevel());
        this.pos = blockEntity.getBlockPos();
        this.block = blockEntity.getBlockState().getBlock();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level() == level && level.getBlockState(pos).is(block)
                && player.canInteractWithBlock(pos, RANGE);
    }

    @Override
    public ContainerLevelAccess levelAccess() {
        return ContainerLevelAccess.create(level, pos);
    }
}
