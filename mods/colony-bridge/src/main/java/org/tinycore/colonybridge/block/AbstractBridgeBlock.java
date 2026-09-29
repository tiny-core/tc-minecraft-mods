package org.tinycore.colonybridge.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.ColonyAccess;

import java.util.List;

/**
 * Base dos blocos que ligam uma rede ME a uma colônia (Ponte e Abastecedor). Cuida de tudo o que é
 * igual entre eles, para cada subclasse só dizer <b>qual</b> block entity, tela e tooltip usa:
 * <ul>
 *   <li>frente virada para quem colocou ({@link #FACING}; os modelos são feitos com a frente para o norte e o
 *       blockstate gira o modelo);</li>
 *   <li>estado visual no blockstate ({@link #STATUS}, atualizado pelo block entity);</li>
 *   <li>recusar a colocação em colônia onde o jogador não tem permissão e gravar o dono;</li>
 *   <li>ticker só no servidor, que chama {@link AbstractBridgeBlockEntity#serverTick()};</li>
 *   <li>avisar o block entity quando um vizinho muda (cabo trocado embaixo);</li>
 *   <li>clique direito: abre a tela para quem pode configurar, senão mostra o estado.</li>
 * </ul>
 * Par do {@link AbstractBridgeBlockEntity}: o bloco é a "casca" no mundo (sem estado próprio) e o
 * block entity guarda os dados e roda a lógica.
 * <p>
 * {@code <E extends AbstractBridgeBlockEntity>} é um generic com restrição, igual a
 * {@code where E : AbstractBridgeBlockEntity} em C#: cada subclasse fixa o tipo do seu block entity.
 */
public abstract class AbstractBridgeBlock<E extends AbstractBridgeBlockEntity> extends Block implements EntityBlock {

    /** Propriedade do blockstate que escolhe o modelo (ver {@code blockstates/*.json}). */
    public static final EnumProperty<BridgeVisualState> STATUS =
            EnumProperty.create("status", BridgeVisualState.class);

    /** Para onde a frente do bloco aponta (só horizontal). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Class<E> entityClass;

    protected AbstractBridgeBlock(Properties props, Class<E> entityClass) {
        super(props);
        this.entityClass = entityClass;
        registerDefaultState(stateDefinition.any().setValue(STATUS, BridgeVisualState.OFFLINE)
                .setValue(FACING, Direction.NORTH));
    }

    /** Tipo registrado do block entity deste bloco (em {@code ModBlockEntities}). */
    protected abstract BlockEntityType<E> blockEntityType();

    /** Nome usado nas chaves de tradução do tooltip ({@code tooltip.tccolonybridge.<nome>.line1/2}). */
    protected abstract String tooltipName();

    /** Título da tela. */
    protected abstract Component menuTitle();

    /** Cria o menu (container) no servidor para o jogador que abriu a tela. */
    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory inventory, E blockEntity);

    /** Declara quais propriedades o bloco tem; o Minecraft gera uma combinação de estado para cada valor. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS, FACING);
    }

    /** Estruturas giradas/espelhadas (ex.: schematics) giram a frente junto. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Dados mandados ao cliente ao abrir a tela ("dados extras" do menu). Padrão: só a posição do bloco;
     * um bloco pode acrescentar o que a tela dele precisa (o Terminal manda também o nome da colônia).
     */
    protected void writeMenuData(RegistryFriendlyByteBuf buf, E blockEntity) {
        buf.writeBlockPos(blockEntity.getBlockPos());
    }

    /** Texto ao passar o mouse sobre o item (inventário, JEI/EMI). */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        String prefix = "tooltip.tccolonybridge." + tooltipName();
        tooltip.add(Component.translatable(prefix + ".line1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(prefix + ".line2").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide || type != blockEntityType()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((AbstractBridgeBlockEntity) be).serverTick();
    }

    /**
     * Retornar null aqui cancela a colocação. Só decide no servidor; no cliente o bloco aparece por um
     * instante e o servidor o remove em seguida (o item volta para a mão).
     * Sem jogador (ex.: deployer sem fake player) dentro de uma colônia também é recusado.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        // Frente virada para o jogador: o oposto da direção para onde ele olha.
        BlockState placed = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        if (level.isClientSide) {
            return placed;
        }
        Player player = context.getPlayer();
        if (ColonyAccess.canPlaceBridge(level, context.getClickedPos(), player == null ? null : player.getUUID())) {
            return placed;
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
        E be = entityAt(level, pos);
        if (!level.isClientSide && placer instanceof Player player && be != null) {
            be.setOwner(player);
        }
    }

    /** Um vizinho mudou (ex.: cabo colocado/trocado embaixo): o block entity reavalia a conexão. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        E be = entityAt(level, pos);
        if (!level.isClientSide && be != null) {
            be.onNeighborChanged();
        }
    }

    /**
     * Clique direito: quem pode configurar abre a tela; os demais só veem o estado na barra de ação.
     * {@code openMenu(provider, pos)} cria o menu no servidor e manda o cliente abrir o seu,
     * enviando a posição do bloco como "dado extra".
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        E be = entityAt(level, pos);
        if (level.isClientSide || be == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!be.canConfigure(player)) {
            player.displayClientMessage(Component.translatable(be.getStatus().translationKey()), true);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> createMenu(containerId, inventory, be), menuTitle()),
                buf -> writeMenuData(buf, be));
        return InteractionResult.CONSUME;
    }

    /** Block entity deste bloco na posição, ou null se não houver (ou for de outro tipo). */
    private @Nullable E entityAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return entityClass.isInstance(be) ? entityClass.cast(be) : null;
    }
}
