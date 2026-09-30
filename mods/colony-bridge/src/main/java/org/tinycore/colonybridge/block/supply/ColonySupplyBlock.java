package org.tinycore.colonybridge.block.supply;

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
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Bloco de Abastecimento da Colônia: mantém itens no armazém e devolve o excedente à rede ME.
 * Todo o comportamento de bloco (permissão, estado visual, ticker, clique direito) vem do
 * {@link AbstractBridgeBlock}; aqui só se liga o {@link ColonySupplyBlockEntity} e a {@link ColonySupplyMenu}.
 */
public class ColonySupplyBlock extends AbstractBridgeBlock<ColonySupplyBlockEntity> {

    public ColonySupplyBlock(Properties props) {
        super(props, ColonySupplyBlockEntity.class);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonySupplyBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<ColonySupplyBlockEntity> blockEntityType() {
        return ModBlockEntities.COLONY_SUPPLY.get();
    }

    @Override
    protected ColonyBlockType colonyBlockType() {
        return ColonyBlockType.SUPPLY;
    }

    @Override
    protected String tooltipName() {
        return "colony_supply";
    }

    @Override
    protected Component menuTitle() {
        return Component.translatable("gui.tccolonybridge.supply.title");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory, ColonySupplyBlockEntity be) {
        return new ColonySupplyMenu(containerId, inventory, be);
    }
}
