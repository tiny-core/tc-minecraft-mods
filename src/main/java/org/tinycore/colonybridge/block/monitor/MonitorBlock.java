package org.tinycore.colonybridge.block.monitor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.multiblock.MonitorFormation;
import org.tinycore.colonybridge.registry.ModBlockEntities;

/**
 * Bloco de monitor. Vários lado a lado na parede, virados para o mesmo lado, formam uma tela única
 * ({@link MonitorFormation}); a tela é desenhada pelo {@code MonitorRenderer} no cliente.
 * <p>
 * A formação usa <b>tick agendado</b> ({@code scheduleTick}): ao colocar/quebrar, o bloco pede ao jogo
 * para ser chamado no próximo tick. Isso garante que o block entity já existe e junta várias mudanças
 * do mesmo tick numa só recalculada.
 */
public class MonitorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Codec exigido pelo Minecraft 1.21 para blocos com direção (usado em serialização de dados). */
    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);

    public MonitorBlock(Properties props) {
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

    /** A tela fica virada para quem colocou. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    /**
     * Servidor: o mestre lê a ponte ligada 1×/s. Cliente: o mestre avança a página da lista.
     * Nenhum dos dois faz nada nos blocos que não são mestre.
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (type != ModBlockEntities.COLONY_MONITOR.get()) {
            return null;
        }
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> ((MonitorBlockEntity) be).clientTick();
        }
        return (lvl, pos, st, be) -> ((MonitorBlockEntity) be).serverTick();
    }

    /**
     * Clique direito na frente da tela: metade direita avança a página da lista, metade esquerda volta.
     * Só muda a exibição no cliente de quem clicou (nenhum dado vai ao servidor).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide && level.getBlockEntity(pos) instanceof MonitorBlockEntity clicked
                && level.getBlockEntity(clicked.getMasterPos()) instanceof MonitorBlockEntity master) {
            float column = clicked.getOffsetX() + horizontalFraction(pos, hit.getLocation(), facing);
            master.turnPage(column >= master.getWidth() / 2f ? 1 : -1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Onde o clique caiu na largura do bloco, de 0 (esquerda de quem olha) a 1 (direita). */
    private static float horizontalFraction(BlockPos pos, Vec3 hit, Direction facing) {
        Direction right = MonitorFormation.right(facing);
        double dx = hit.x - (pos.getX() + 0.5);
        double dz = hit.z - (pos.getZ() + 0.5);
        return (float) (dx * right.getStepX() + dz * right.getStepZ() + 0.5);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Ao quebrar, os vizinhos na parede recalculam (a tela pode ter se dividido em duas). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        BlockPos link = removed && level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor ? monitor.getLink() : null;
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!removed || level.isClientSide) {
            return;
        }
        Direction right = MonitorFormation.right(state.getValue(FACING));
        for (Direction step : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
            BlockPos neighbor = pos.relative(step);
            if (!level.getBlockState(neighbor).is(this)) {
                continue;
            }
            // O mestre quebrado passa a ligação para um vizinho; a formação a leva ao novo mestre.
            if (link != null && level.getBlockEntity(neighbor) instanceof MonitorBlockEntity other) {
                other.setLink(link);
                link = null;
            }
            level.scheduleTick(neighbor, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MonitorFormation.rebuild(level, pos);
    }
}
