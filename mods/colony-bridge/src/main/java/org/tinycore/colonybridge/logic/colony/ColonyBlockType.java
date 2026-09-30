package org.tinycore.colonybridge.logic.colony;

/**
 * Tipos de bloco TC que só podem existir <b>um por colônia</b>. Cada block entity diz o seu tipo
 * ({@code AbstractBridgeBlockEntity.colonyBlockType()}) e o {@link ColonyBlockRegistry} guarda uma vaga por
 * colônia e tipo.
 * <p>
 * O nome do valor é salvo no mundo: renomear um valor faz os blocos perderem a vaga (e recuperá-la no
 * próximo ciclo, se não houver outro).
 */
public enum ColonyBlockType {
    BRIDGE,
    SUPPLY,
    TERMINAL,
    CHUNK_LOADER
}
