package org.tinycore.colonybridge.block.bridge;

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
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Bloco da Ponte ME da Colônia: atende os pedidos em aberto da colônia com itens da rede ME.
 * Todo o comportamento de bloco (permissão, estado visual, ticker, clique direito) vem do
 * {@link AbstractBridgeBlock}; aqui só se liga o {@link ColonyBridgeBlockEntity} e a {@link ColonyBridgeMenu}.
 */
public class ColonyBridgeBlock extends AbstractBridgeBlock<ColonyBridgeBlockEntity> {

    public ColonyBridgeBlock(Properties props) {
        super(props, ColonyBridgeBlockEntity.class);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonyBridgeBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<ColonyBridgeBlockEntity> blockEntityType() {
        return ModBlockEntities.COLONY_BRIDGE.get();
    }

    @Override
    protected ColonyBlockType colonyBlockType() {
        return ColonyBlockType.BRIDGE;
    }

    @Override
    protected String tooltipName() {
        return "colony_bridge";
    }

    @Override
    protected Component menuTitle() {
        return Component.translatable("gui.tccolonybridge.title");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory, ColonyBridgeBlockEntity be) {
        return new ColonyBridgeMenu(containerId, inventory, be);
    }
}
