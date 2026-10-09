package org.tinycore.cloud.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.menu.CloudLinkMenu;
import org.tinycore.cloud.registry.ModBlockEntities;

/**
 * <b>TC Cloud Link</b>: o ponto de acesso à nuvem no mundo. Liga-se à rede AE2 por qualquer lado e expõe o canal
 * do dono como armazenamento (modo e prioridade na tela). Só quem colocou abre a tela. Quebrar o bloco não
 * derruba itens: eles estão na nuvem.
 *
 * <p><b>Dois blocos de altura</b>, como uma porta: a metade de baixo ({@link #HALF} = lower) tem o block entity, o
 * nó AE2 e o modelo inteiro (27 px de altura); a de cima (upper) é invisível e só existe para ocupar o espaço e
 * receber cliques (o jogo só testa o formato de um bloco dentro do próprio cubo, então sem ela a nuvem não
 * responderia). Quebrar qualquer metade quebra as duas; o item sai só da de baixo (loot table).
 *
 * <p>{@link #ACTIVE} (só na metade de baixo) escolhe o modelo aceso ou apagado; quem o atualiza é o
 * {@link CloudLinkBlockEntity}.
 *
 * <p>{@code BaseEntityBlock} é o bloco "com block entity" do Minecraft; o {@code codec()} é exigido pelo 1.21
 * para blocos registrados (descreve como recriar o bloco a partir das propriedades).
 */
public class CloudLinkBlock extends BaseEntityBlock {

    public static final MapCodec<CloudLinkBlock> CODEC = simpleCodec(CloudLinkBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** Formato do modelo inteiro, a partir do chão da metade de baixo. */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2, 0, 2, 14, 3.5, 14),     // base e emissor
        Block.box(5, 3.5, 5, 11, 17, 11),    // colunas de dados
        Block.box(1, 17, 1, 15, 27, 15));    // nuvem
    /** O mesmo formato visto da metade de cima (um bloco abaixo): a seleção mostra o Link inteiro nas duas. */
    private static final VoxelShape UPPER_SHAPE = SHAPE.move(0, -1, 0);

    public CloudLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, HALF);
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? SHAPE : UPPER_SHAPE;
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL; // BaseEntityBlock é invisível por padrão (a metade de cima usa um modelo vazio)
    }

    /** Só a metade de baixo tem block entity (nó AE2, dono, canal). */
    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new CloudLinkBlockEntity(pos, state) : null;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state,
                                                                            @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.CLOUD_LINK.get(),
            (l, p, s, be) -> be.serverTick());
    }

    // ---------------------------------------------------------------- duas metades

    /** Só coloca se o espaço de cima estiver livre (e dentro do limite de altura do mundo). */
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState();
    }

    /** Coloca a metade de cima e grava o dono (na de baixo). */
    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (!level.isClientSide && placer instanceof Player player
            && level.getBlockEntity(pos) instanceof CloudLinkBlockEntity link) {
            link.setOwner(player);
        }
    }

    /** A metade de cima só existe em cima da de baixo. */
    @Override
    protected boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /**
     * Uma metade sumiu (quebrada, explodida, pistão)? A outra vira ar. Como na porta do vanilla: se a de cima for
     * quebrada, a de baixo é destruída por aqui e derruba o item.
     */
    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
                                              @NotNull BlockState neighbor, @NotNull LevelAccessor level,
                                              @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardsOther = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardsOther && !(neighbor.is(this) && neighbor.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * Quebrar a metade de cima no criativo (ou sem a ferramenta certa) não pode derrubar o item pela de baixo:
     * remove a de baixo antes, sem drop (o que o vanilla faz com {@code DoublePlantBlock.preventDropFromBottomPart}).
     */
    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                                                 @NotNull Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER
                && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower)); // som e partículas de quebra
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---------------------------------------------------------------- tela

    /** Clique em qualquer metade abre a tela (a de cima repassa para a de baixo, onde está o block entity). */
    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, Level level, @NotNull BlockPos pos,
                                                        @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (!(level.getBlockEntity(lower) instanceof CloudLinkBlockEntity link) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!link.isOwner(player)) {
            player.displayClientMessage(Component.translatable("gui.tccloud.not_owner", link.ownerName()), true);
            return InteractionResult.CONSUME;
        }
        CloudLinkMenu.open(sp, link);
        return InteractionResult.CONSUME;
    }
}
