package org.tinycore.colonybridge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.logic.colony.ColonyBlockRegistry;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.logic.colony.ColonySlotRule;
import org.tinycore.colonybridge.logic.colony.ColonySlotRule.HolderState;

/**
 * Aplica a regra "um bloco de cada tipo por colônia" no mundo: confere o bloco registrado no
 * {@link ColonyBlockRegistry} e decide com o {@link ColonySlotRule}.
 * <p>
 * Resolve o problema do <b>registro velho</b> (bloco removido sem passar pelo {@code onRemove}: edição do
 * mundo, chunk apagado...): quando alguém disputa a vaga e o chunk do registrado está carregado mas o bloco
 * não está mais lá (ou saiu da colônia), o registro é descartado e a vaga passa para quem pediu. Se o chunk
 * não está carregado, a vaga continua do registrado (nunca se carrega chunk só para conferir).
 * <p>
 * Usado pelo {@link AbstractBridgeBlock} (recusar a colocação) e pelo {@link AbstractBridgeBlockEntity}
 * (ocupar a vaga a cada ciclo e soltar quando o bloco é removido).
 */
public final class ColonySlots {

    private ColonySlots() {}

    /**
     * Posição de <b>outro</b> bloco do tipo que ocupa a vaga da colônia, ou null se o bloco em {@code mine}
     * pode ficar com ela. Registro velho é descartado aqui.
     */
    public static @Nullable BlockPos occupant(ServerLevel level, String colony, ColonyBlockType type, BlockPos mine) {
        ColonyBlockRegistry registry = ColonyBlockRegistry.get(level);
        BlockPos holder = registry.holder(colony, type);
        if (holder == null || holder.equals(mine)) {
            return null;
        }
        HolderState state = stateAt(level, holder, colony, type);
        if (!ColonySlotRule.canClaim(holder.asLong(), mine.asLong(), state)) {
            return holder;
        }
        registry.release(colony, type, holder);
        return null;
    }

    /** Tenta ocupar a vaga; true se ela é (ou passou a ser) deste bloco. */
    public static boolean claim(ServerLevel level, String colony, ColonyBlockType type, BlockPos mine) {
        if (occupant(level, colony, type, mine) != null) {
            return false;
        }
        ColonyBlockRegistry.get(level).set(colony, type, mine);
        return true;
    }

    /** Solta a vaga, se for deste bloco. */
    public static void release(ServerLevel level, String colony, ColonyBlockType type, BlockPos mine) {
        ColonyBlockRegistry.get(level).release(colony, type, mine);
    }

    /** Situação do bloco registrado, sem carregar chunk ({@code isLoaded} antes de ler o bloco). */
    private static HolderState stateAt(ServerLevel level, BlockPos pos, String colony, ColonyBlockType type) {
        if (!level.isLoaded(pos)) {
            return HolderState.UNLOADED;
        }
        BlockEntity be = level.getBlockEntity(pos);
        boolean same = be instanceof AbstractBridgeBlockEntity bridge && !bridge.isRemoved()
                && bridge.colonyBlockType() == type
                && colony.equals(ColonyAccess.colonyKeyAt(level, pos));
        return same ? HolderState.VALID : HolderState.GONE;
    }
}
