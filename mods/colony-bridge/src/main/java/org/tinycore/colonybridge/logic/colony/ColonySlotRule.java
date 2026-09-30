package org.tinycore.colonybridge.logic.colony;

import org.jetbrains.annotations.Nullable;

/**
 * Regra pura da vaga "um bloco de cada tipo por colônia": decide se um bloco pode ficar com a vaga,
 * dado quem está registrado nela e em que situação está. Só números e um enum, para ser testada sem o jogo.
 * <p>
 * Quem descobre a situação do bloco registrado (carregado? ainda existe?) é o {@code ColonySlots}, na
 * camada de blocos; quem guarda a vaga é o {@link ColonyBlockRegistry}.
 */
public final class ColonySlotRule {

    /** Situação do bloco que está registrado na vaga. */
    public enum HolderState {
        /** Carregado, é do mesmo tipo e continua na mesma colônia: a vaga é dele. */
        VALID,
        /** Carregado, mas o bloco sumiu (quebrado sem aviso, trocado) ou saiu da colônia: registro velho. */
        GONE,
        /** Chunk não carregado: não dá para conferir, então a vaga continua dele. */
        UNLOADED
    }

    private ColonySlotRule() {}

    /**
     * true se o bloco em {@code mine} pode ocupar a vaga.
     *
     * @param registered posição registrada na vaga ({@code BlockPos.asLong()}), ou null se livre
     * @param mine       posição de quem quer a vaga
     * @param state      situação do registrado (ignorada se a vaga está livre ou já é de {@code mine})
     */
    public static boolean canClaim(@Nullable Long registered, long mine, HolderState state) {
        return registered == null || registered == mine || state == HolderState.GONE;
    }
}
