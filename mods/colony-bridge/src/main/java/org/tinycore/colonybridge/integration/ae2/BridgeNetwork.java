package org.tinycore.colonybridge.integration.ae2;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;

import java.util.Set;

/**
 * Pontes presentes numa rede ME. {@code getMachines} do AE2 lista os blocos da rede cujo nó pertence a
 * uma classe; o nó da Ponte pertence ao próprio {@link ColonyBridgeBlockEntity}.
 * <p>
 * Usado para "só uma Ponte por rede" e para o Terminal do Armazém achar a Ponte da colônia.
 */
public final class BridgeNetwork {

    private BridgeNetwork() {}

    public static int bridgeCount(IGrid grid) {
        return grid.getMachines(ColonyBridgeBlockEntity.class).size();
    }

    /** A Ponte da rede, se houver exatamente uma; senão null. */
    public static @Nullable ColonyBridgeBlockEntity singleBridge(IGrid grid) {
        Set<ColonyBridgeBlockEntity> bridges = grid.getMachines(ColonyBridgeBlockEntity.class);
        return bridges.size() == 1 ? bridges.iterator().next() : null;
    }
}
