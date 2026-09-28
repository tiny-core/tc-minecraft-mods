package org.tinycore.colonybridge.block.monitor;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.multiblock.MonitorFormation;

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
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!removed || level.isClientSide) {
            return;
        }
        Direction right = MonitorFormation.right(state.getValue(FACING));
        for (Direction step : new Direction[]{right, right.getOpposite(), Direction.UP, Direction.DOWN}) {
            BlockPos neighbor = pos.relative(step);
            if (level.getBlockState(neighbor).is(this)) {
                level.scheduleTick(neighbor, this, 1);
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MonitorFormation.rebuild(level, pos);
    }
}
