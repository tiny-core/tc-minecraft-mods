package org.tinycore.colonybridge.block.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderMenu;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Bloco do TC Colony Chunk Loader. Colocação, dono, visual, ticker e tela vêm do {@link AbstractBridgeBlock};
 * a lógica está no {@link ColonyChunkLoaderBlockEntity}.
 */
public class ColonyChunkLoaderBlock extends AbstractBridgeBlock<ColonyChunkLoaderBlockEntity> {

    public ColonyChunkLoaderBlock(Properties props) {
        super(props, ColonyChunkLoaderBlockEntity.class);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonyChunkLoaderBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<ColonyChunkLoaderBlockEntity> blockEntityType() {
        return ModBlockEntities.CHUNK_LOADER.get();
    }

    @Override
    protected ColonyBlockType colonyBlockType() {
        return ColonyBlockType.CHUNK_LOADER;
    }

    @Override
    protected String tooltipName() {
        return "colony_chunk_loader";
    }

    @Override
    protected Component menuTitle() {
        return Component.translatable("block.tccolonybridge.colony_chunk_loader");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory, ColonyChunkLoaderBlockEntity be,
                                               MenuAccess access) {
        return new ChunkLoaderMenu(containerId, inventory, be, access);
    }
}
