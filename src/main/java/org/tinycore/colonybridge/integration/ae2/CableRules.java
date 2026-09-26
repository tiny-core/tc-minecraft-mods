package org.tinycore.colonybridge.integration.ae2;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AECableType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Regra de conexão da ponte com a rede ME: só por baixo e só com cabo comum (vidro, coberto ou smart,
 * os de 8 canais). Cabo denso, outro dispositivo AE2 encostado ou qualquer outra coisa é recusado.
 * <p>
 * A checagem é pelo <b>tipo</b> do cabo, não pela contagem de canais, porque a quantidade de canais
 * muda com a config do AE2 (modo x2/x4/infinito) e o tipo não.
 * <p>
 * Primeiro arquivo de {@code integration/ae2/}: uso da API do AE2 que não é o nó da grid em si.
 * Usado pelo {@code ColonyBridgeBlockEntity} para decidir se expõe o lado de baixo.
 */
public final class CableRules {

    /** Único lado pelo qual a ponte aceita conexão. */
    public static final Direction CONNECTION_SIDE = Direction.DOWN;

    private CableRules() {}

    /**
     * true se abaixo da ponte há um cabo AE2 comum.
     * {@code getPart(null)} devolve o cabo do centro de um "cable bus" (o bloco do AE2 que guarda
     * cabos e partes); com {@code Direction} ele devolveria a parte presa naquele lado.
     */
    public static boolean hasAllowedCable(Level level, BlockPos bridgePos) {
        BlockPos pos = bridgePos.relative(CONNECTION_SIDE);
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof IPartHost host)) {
            return false;
        }
        if (!(host.getPart(null) instanceof ICablePart cable)) {
            return false;
        }
        AECableType type = cable.getCableConnectionType();
        return type == AECableType.GLASS || type == AECableType.COVERED || type == AECableType.SMART;
    }
}
