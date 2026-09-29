package org.tinycore.colonybridge.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.monitor.MonitorBlock;
import org.tinycore.colonybridge.block.monitor.MonitorBlockEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Forma as telas de monitor: junta monitores vizinhos, virados para o mesmo lado, numa tela única.
 * <p>
 * Regra: o grupo de monitores conectados (na parede, sem diagonais) precisa ser um <b>retângulo
 * completo</b> de no máximo {@code monitorMaxWidth × monitorMaxHeight}. Se for, o canto inferior
 * esquerdo (visto de frente) vira o <b>mestre</b> e os outros guardam a posição relativa a ele.
 * Se não for, cada bloco fica sozinho marcado como inválido. A conta do retângulo fica no
 * {@link MonitorShape} (regra pura, testada sem o jogo); aqui fica a parte que lê o mundo.
 * <p>
 * Só roda quando um monitor é colocado ou quebrado (via tick agendado do {@link MonitorBlock}),
 * nunca por tick contínuo. Não carrega chunks: vizinho em chunk descarregado conta como "não é monitor".
 */
public final class MonitorFormation {

    /** Teto da busca, para uma parede gigante de monitores não travar o servidor. */
    private static final int SEARCH_LIMIT = 512;

    private MonitorFormation() {}

    /**
     * Direção "direita" de quem olha a tela de frente. Ex.: tela virada para o norte → quem olha está
     * ao norte olhando para o sul → a direita dele é o oeste.
     */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Recalcula a tela que contém {@code start}. */
    public static void rebuild(Level level, BlockPos start) {
        BlockState startState = level.getBlockState(start);
        if (!(startState.getBlock() instanceof MonitorBlock)) {
            return;
        }
        Direction facing = startState.getValue(MonitorBlock.FACING);
        Direction right = right(facing);

        Set<BlockPos> group = new HashSet<>();
        boolean overflow = collect(level, start, facing, right, group);

        MonitorShape shape = new MonitorShape();
        for (BlockPos pos : group) {
            shape.add(along(pos, start, right), pos.getY() - start.getY());
        }
        boolean valid = !overflow
                && shape.isValid(Config.MONITOR_MAX_WIDTH.get(), Config.MONITOR_MAX_HEIGHT.get());

        BlockPos link = valid ? takeLink(level, group) : null;
        for (BlockPos pos : group) {
            if (!(level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor)) {
                continue;
            }
            if (valid) {
                monitor.setStructure(along(pos, start, right) - shape.minU(), pos.getY() - start.getY() - shape.minV(),
                        shape.width(), shape.height(), true);
                if (monitor.isMaster() && link != null) {
                    monitor.setLink(link); // a ligação passa para o novo mestre
                }
            } else {
                monitor.setStructure(0, 0, 1, 1, false);
            }
        }
    }

    /**
     * Retira a ligação de qualquer bloco do grupo (ela fica só no mestre). Assim, ao aumentar ou
     * diminuir a tela, a ponte ligada não se perde mesmo que o mestre passe a ser outro bloco.
     */
    private static BlockPos takeLink(Level level, Set<BlockPos> group) {
        BlockPos found = null;
        for (BlockPos pos : group) {
            if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor && monitor.getLink() != null) {
                if (found == null) {
                    found = monitor.getLink();
                }
                monitor.setLink(null);
            }
        }
        return found;
    }

    /**
     * Busca em largura (BFS) pelos monitores conectados ao {@code start} na mesma parede.
     *
     * @return true se passou do {@link #SEARCH_LIMIT} (grupo grande demais: inválido)
     */
    private static boolean collect(Level level, BlockPos start, Direction facing, Direction right, Set<BlockPos> out) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        out.add(start);
        Direction[] steps = {right, right.getOpposite(), Direction.UP, Direction.DOWN};
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction step : steps) {
                BlockPos next = current.relative(step);
                if (out.contains(next) || !isMonitor(level, next, facing)) {
                    continue;
                }
                if (out.size() >= SEARCH_LIMIT) {
                    return true;
                }
                out.add(next);
                queue.add(next);
            }
        }
        return false;
    }

    private static boolean isMonitor(Level level, BlockPos pos, Direction facing) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MonitorBlock && state.getValue(MonitorBlock.FACING) == facing;
    }

    /** Distância de {@code origin} até {@code pos} medida na direção {@code axis} (pode ser negativa). */
    private static int along(BlockPos pos, BlockPos origin, Direction axis) {
        return (pos.getX() - origin.getX()) * axis.getStepX() + (pos.getZ() - origin.getZ()) * axis.getStepZ();
    }
}
