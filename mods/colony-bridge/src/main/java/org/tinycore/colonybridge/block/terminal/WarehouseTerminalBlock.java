package org.tinycore.colonybridge.block.terminal;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;

import java.util.List;

/**
 * Terminal do Armazém: bloco que abre uma tela com todos os itens dos racks do armazém da colônia, para
 * tirar e guardar como num terminal do AE2. Não usa rede ME nem guarda estado, por isso não tem block
 * entity: a colônia é achada pela posição e a permissão é conferida a cada abertura e a cada clique.
 * <p>
 * {@code HorizontalDirectionalBlock} dá ao bloco a propriedade {@code FACING} (a frente fica virada para
 * quem colocou), igual ao monitor.
 */
public class WarehouseTerminalBlock extends HorizontalDirectionalBlock {

    /** Codec exigido pelo Minecraft 1.21 para blocos com direção. */
    public static final MapCodec<WarehouseTerminalBlock> CODEC = simpleCodec(WarehouseTerminalBlock::new);

    public WarehouseTerminalBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Recusa a colocação numa colônia onde o jogador não tem permissão (mesma regra da ponte). */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide
                || ColonyAccess.canPlaceBridge(level, context.getClickedPos(), player == null ? null : player.getUUID())) {
            return state;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.tccolonybridge.no_permission"), true);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tccolonybridge.warehouse_terminal.line1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.tccolonybridge.warehouse_terminal.line2").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Clique direito: abre a tela se a posição está numa colônia e o jogador tem permissão; senão explica
     * o motivo na barra de ação. {@code openMenu} manda ao cliente a posição e o nome da colônia.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        String colony = ColonyAccess.colonyNameAt(level, pos);
        if (colony.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tccolonybridge.terminal.no_colony"), true);
            return InteractionResult.CONSUME;
        }
        if (ColonyAccess.accessibleRacks(level, pos, player.getUUID()) == null) {
            player.displayClientMessage(Component.translatable("message.tccolonybridge.no_permission"), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WarehouseTerminalMenu(id, inventory, pos),
                Component.translatable("gui.tccolonybridge.terminal.title")),
                buf -> WarehouseTerminalMenu.writeOpenData(buf, pos, colony));
        return InteractionResult.CONSUME;
    }
}
