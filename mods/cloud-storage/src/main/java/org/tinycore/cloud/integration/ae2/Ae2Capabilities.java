package org.tinycore.cloud.integration.ae2;

import appeng.api.AECapabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.tinycore.cloud.registry.ModBlockEntities;

/**
 * Expõe o nó da grade do TC Cloud Link para os cabos do AE2: o AE2 só acha vizinhos por esta capability
 * ({@code GridHelper.getNodeHost}). Sem ela o cabo não se liga ao bloco.
 */
public final class Ae2Capabilities {

    private Ae2Capabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ModBlockEntities.CLOUD_LINK.get(),
                (be, ctx) -> be);
    }
}
