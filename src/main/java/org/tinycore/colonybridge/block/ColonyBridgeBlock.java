package org.tinycore.colonybridge.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.registry.ModRegistries;

import java.util.List;

/**
 * Bloco da Ponte ME da Colônia. Cria o {@link ColonyBridgeBlockEntity} (onde fica o nó AE2 e a lógica),
 * impede a colocação em colônias onde o jogador não tem permissão e mostra o estado no clique direito.
 * O estado visual ({@link #STATUS}) é atualizado pelo block entity; aqui só é declarado.
 */
public class ColonyBridgeBlock extends Block implements EntityBlock {

    /** Propriedade do blockstate que escolhe o modelo (ver {@code blockstates/colony_bridge.json}). */
    public static final EnumProperty<BridgeVisualState> STATUS =
            EnumProperty.create("status", BridgeVisualState.class);

    public ColonyBridgeBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(STATUS, BridgeVisualState.OFFLINE));
    }

    /** Declara quais propriedades o bloco tem; o Minecraft gera uma combinação de estado para cada valor. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS);
    }

    /** Texto ao passar o mouse sobre o item (inventário, JEI/EMI). */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tccolonybridge.colony_bridge.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tccolonybridge.colony_bridge.line2")
                .withStyle(ChatFormatting.GRAY));
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

    /**
     * Retornar null aqui cancela a colocação. Só decide no servidor; no cliente o bloco aparece por um
     * instante e o servidor o remove em seguida (o item volta para a mão).
     * Sem jogador (ex.: deployer sem fake player) dentro de uma colônia também é recusado.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return defaultBlockState();
        }
        Player player = context.getPlayer();
        if (ColonyAccess.canPlaceBridge(level, context.getClickedPos(), player == null ? null : player.getUUID())) {
            return defaultBlockState();
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.tccolonybridge.no_permission"), true);
        }
        return null;
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
