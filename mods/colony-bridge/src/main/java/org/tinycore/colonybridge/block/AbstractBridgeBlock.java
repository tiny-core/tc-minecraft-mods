package org.tinycore.colonybridge.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.menu.tablet.TabletView;

import java.util.List;

/**
 * Base dos blocos que ligam uma rede ME a uma colônia (Ponte e Abastecedor). Cuida de tudo o que é
 * igual entre eles, para cada subclasse só dizer <b>qual</b> block entity, tela e tooltip usa:
 * <ul>
 *   <li>frente virada para quem colocou ({@link #FACING}; os modelos são feitos com a frente para o norte e o
 *       blockstate gira o modelo);</li>
 *   <li>estado visual no blockstate ({@link #STATUS}, atualizado pelo block entity);</li>
 *   <li>recusar a colocação em colônia onde o jogador não tem permissão ou que já tem um bloco deste tipo
 *       ({@link ColonySlots}), e gravar o dono;</li>
 *   <li>soltar a vaga da colônia quando o bloco sai do mundo ({@link #onRemove});</li>
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

    /** Tipo na regra "um de cada tipo por colônia" (o mesmo do block entity). */
    protected abstract ColonyBlockType colonyBlockType();

    /** Nome usado nas chaves de tradução do tooltip ({@code tooltip.tccolonybridge.<nome>.line1/2}). */
    protected abstract String tooltipName();

    /** Título da tela. */
    protected abstract Component menuTitle();

    /**
     * Cria o menu (container) no servidor para o jogador que abriu a tela.
     *
     * @param access por onde a tela foi aberta (clique no bloco ou tablet): decide quando ela continua válida
     */
    protected abstract AbstractContainerMenu createMenu(int containerId, Inventory inventory, E blockEntity,
                                                        MenuAccess access);

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
        BlockPos pos = context.getClickedPos();
        if (!ColonyAccess.canPlaceBridge(level, pos, player == null ? null : player.getUUID())) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.tccolonybridge.no_permission"), true);
            }
            return null;
        }
        BlockPos other = occupantAt((ServerLevel) level, pos);
        if (other == null) {
            return placed;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.tccolonybridge.already_in_colony",
                    getName(), other.getX(), other.getY(), other.getZ()), true);
        }
        return null;
    }

    /** Outro bloco deste tipo na colônia da posição, ou null (fora de colônia não há limite). */
    private @Nullable BlockPos occupantAt(ServerLevel level, BlockPos pos) {
        String colony = ColonyAccess.colonyKeyAt(level, pos);
        return colony == null ? null : ColonySlots.occupant(level, colony, colonyBlockType(), pos);
    }

    /**
     * O bloco saiu do mundo ou foi trocado por outro (quebra, explosão, {@code /setblock}): solta a vaga da
     * colônia. Não roda quando o chunk só descarrega, nem quando muda só o estado deste bloco (visual/frente).
     * O {@code super} é quem remove o block entity, por isso a vaga é solta antes.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            E be = entityAt(level, pos);
            if (be != null) {
                be.releaseColonySlot(serverLevel);
                be.onBroken(serverLevel);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        E be = entityAt(level, pos);
        if (!level.isClientSide && placer instanceof Player player && be != null) {
            be.setOwner(player);
        }
        if (level instanceof ServerLevel serverLevel && be != null) {
            be.holdsColonySlot(serverLevel); // ocupa a vaga já, sem esperar o primeiro ciclo
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
        open((ServerPlayer) player, be, MenuAccess.block(be));
        return InteractionResult.CONSUME;
    }

    /**
     * Abre a tela deste bloco pelo tablet. Quem chama ({@code TabletOpener}) já achou o bloco pelo registro da
     * colônia e conferiu permissão e bateria.
     *
     * @return false se o block entity não é deste bloco
     */
    public boolean openRemote(ServerPlayer player, BlockEntity blockEntity, MenuAccess access) {
        if (!entityClass.isInstance(blockEntity)) {
            return false;
        }
        open(player, entityClass.cast(blockEntity), access);
        return true;
    }

    /**
     * {@code openMenu(provider, dados)} cria o menu no servidor e manda o cliente abrir o seu com os "dados extras":
     * os do bloco ({@link #writeMenuData}) e, no fim, as abas do tablet ({@link TabletView}, ausente no clique).
     */
    private void open(ServerPlayer player, E be, MenuAccess access) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> createMenu(containerId, inventory, be, access), menuTitle()),
                buf -> {
                    writeMenuData(buf, be);
                    TabletView.write(buf, access.tabletView());
                });
    }

    /** Block entity deste bloco na posição, ou null se não houver (ou for de outro tipo). */
    private @Nullable E entityAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return entityClass.isInstance(be) ? entityClass.cast(be) : null;
    }
}
