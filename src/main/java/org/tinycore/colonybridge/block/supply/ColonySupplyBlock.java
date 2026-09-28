package org.tinycore.colonybridge.block.supply;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import org.tinycore.colonybridge.block.BridgeVisualState;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.menu.ColonySupplyMenu;
import org.tinycore.colonybridge.registry.ModBlockEntities;

import java.util.List;

/**
 * Bloco de Abastecimento da Colônia. Espelha o {@code ColonyBridgeBlock}: mesma regra de permissão para
 * colocar, mesmo estado visual e clique direito abrindo a tela para quem pode configurar.
 */
public class ColonySupplyBlock extends Block implements EntityBlock {

    /** Propriedade do blockstate que escolhe o modelo (ver {@code blockstates/colony_supply.json}). */
    public static final EnumProperty<BridgeVisualState> STATUS =
            EnumProperty.create("status", BridgeVisualState.class);

    public ColonySupplyBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(STATUS, BridgeVisualState.OFFLINE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tccolonybridge.colony_supply.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tccolonybridge.colony_supply.line2")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ColonySupplyBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.COLONY_SUPPLY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((ColonySupplyBlockEntity) be).serverTick();
    }

    /** Retornar null cancela a colocação: sem permissão na colônia, o bloco não fica. */
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ColonySupplyBlockEntity be) {
            be.setOwner(player);
        }
    }

    /** Um vizinho mudou (ex.: cabo colocado/trocado embaixo): o bloco reavalia a conexão. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ColonySupplyBlockEntity be) {
            be.onNeighborChanged();
        }
    }

    /** Clique direito: quem pode configurar abre a tela; os demais só veem o estado na barra de ação. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ColonySupplyBlockEntity be)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!be.canConfigure(player)) {
            player.displayClientMessage(Component.translatable(be.getStatus().translationKey()), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new ColonySupplyMenu(containerId, inventory, be),
                Component.translatable("gui.tccolonybridge.supply.title")), pos);
        return InteractionResult.CONSUME;
    }
}
