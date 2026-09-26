package org.tinycore.colonybridge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.registry.ModRegistries;

public class ColonyBridgeBlock extends Block implements EntityBlock {

    public ColonyBridgeBlock(Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonyBridgeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || type != ModRegistries.COLONY_BRIDGE_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((ColonyBridgeBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ColonyBridgeBlockEntity be) {
            be.setOwner(player);
        }
    }

    /** Clique direito mostra o estado atual (debug simples até existir GUI). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ColonyBridgeBlockEntity be) {
            player.displayClientMessage(Component.translatable(be.getStatus().translationKey()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
