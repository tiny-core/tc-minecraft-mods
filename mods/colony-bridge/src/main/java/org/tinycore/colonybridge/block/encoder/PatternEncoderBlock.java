package org.tinycore.colonybridge.block.encoder;

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
import org.tinycore.colonybridge.menu.encoder.PatternEncoderMenu;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Bloco do TC Pattern Encoder: transforma pedidos da colônia sem padrão no AE2 em padrões de crafting. Colocação,
 * dono, visual, ticker, cabo e tela vêm do {@link AbstractBridgeBlock}; a lógica está no
 * {@link PatternEncoderBlockEntity}.
 */
public class PatternEncoderBlock extends AbstractBridgeBlock<PatternEncoderBlockEntity> {

    public PatternEncoderBlock(Properties props) {
        super(props, PatternEncoderBlockEntity.class);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatternEncoderBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<PatternEncoderBlockEntity> blockEntityType() {
        return ModBlockEntities.PATTERN_ENCODER.get();
    }

    @Override
    protected ColonyBlockType colonyBlockType() {
        return ColonyBlockType.PATTERN_ENCODER;
    }

    @Override
    protected String tooltipName() {
        return "pattern_encoder";
    }

    @Override
    protected Component menuTitle() {
        return Component.translatable("block.tccolonybridge.pattern_encoder");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory, PatternEncoderBlockEntity be,
                                               MenuAccess access) {
        return new PatternEncoderMenu(containerId, inventory, be, access);
    }
}
