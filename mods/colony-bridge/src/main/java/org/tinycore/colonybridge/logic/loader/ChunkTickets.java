package org.tinycore.colonybridge.logic.loader;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.logic.colony.ColonyBlockRegistry;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "Tickets" de chunk do Chunk Loader. Um ticket é o pedido oficial do NeoForge para manter um chunk carregado,
 * com um dono (aqui, a posição do bloco loader). O NeoForge salva os tickets e os restaura ao iniciar o servidor.
 * <p>
 * Ao restaurar, o {@link #validate} descarta os tickets de posições que não são mais o loader registrado de uma
 * colônia (bloco removido sem aviso, mundo editado): assim nenhum chunk fica carregado para sempre por engano. Os
 * que valem continuam, e o próprio loader (cujo chunk fica carregado) confere o resto no primeiro ciclo.
 * <p>
 * Tickets "ticking" ({@code true}): o chunk roda de verdade (plantas, cidadãos, máquinas), não só fica na memória.
 */
public final class ChunkTickets {

    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "chunk_loader"), ChunkTickets::validate);

    private ChunkTickets() {}

    /** Registro do controlador (evento do barramento do mod). */
    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Força ({@code add = true}) ou solta um chunk em nome do loader em {@code owner}. */
    public static void force(ServerLevel level, BlockPos owner, long chunk, boolean add) {
        CONTROLLER.forceChunk(level, owner, ChunkSelection.x(chunk), ChunkSelection.z(chunk), add, true);
    }

    /** Ao iniciar o servidor: descarta tickets de posições que não são o loader registrado de uma colônia. */
    private static void validate(ServerLevel level, TicketHelper helper) {
        Set<BlockPos> valid = new HashSet<>();
        String dimension = level.dimension().location().toString();
        Map<String, BlockPos> loaders = ColonyBlockRegistry.get(level).holders(ColonyBlockType.CHUNK_LOADER);
        loaders.forEach((colony, pos) -> {
            if (dimension.equals(Objects.toString(ColonyAccess.dimensionOf(colony)))) {
                valid.add(pos);
            }
        });
        for (BlockPos owner : Set.copyOf(helper.getBlockTickets().keySet())) {
            if (!valid.contains(owner)) {
                ColonyBridgeMod.LOG.info("Chunk Loader: soltando chunks de um loader que não existe mais em {}", owner);
                helper.removeAllTickets(owner);
            }
        }
    }
}
