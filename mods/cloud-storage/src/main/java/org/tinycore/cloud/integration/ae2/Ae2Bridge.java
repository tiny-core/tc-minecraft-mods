package org.tinycore.cloud.integration.ae2;

import appeng.api.AECapabilities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.block.CloudLinkBlockEntity;
import org.tinycore.cloud.block.LinkNetwork;
import org.tinycore.cloud.item.ContentProbe;
import org.tinycore.cloud.registry.ModBlockEntities;

import java.util.List;

/**
 * Tudo o que o mod pede ao AE2, num lugar só. <b>Só é carregada com o AE2 instalado</b>: quem chama é o
 * {@code Ae2Compat}, que confere antes. Fora deste pacote, nenhuma classe do mod importa o AE2; assim o
 * TC Cloud Storage também roda sem ele (o Link funciona só pela tela).
 */
public final class Ae2Bridge {

    private Ae2Bridge() {}

    /** Registros no barramento do mod (a capability que deixa o cabo do AE2 achar o nó do Link). */
    public static void init(@NotNull IEventBus modBus) {
        modBus.addListener(Ae2Bridge::registerCapabilities);
    }

    /** A ligação do Link com a rede AE2 (nó da grade + montagem do canal). */
    public static @NotNull LinkNetwork network(@NotNull CloudLinkBlockEntity link) {
        return new CloudLinkNode(link);
    }

    /** Sondas de conteúdo do AE2 (célula de armazenamento com itens dentro não pode ir para a nuvem). */
    public static @NotNull List<ContentProbe> contentProbes() {
        return List.of(new Ae2CellProbe());
    }

    /**
     * O AE2 só acha vizinhos pela capability {@code IN_WORLD_GRID_NODE_HOST}; sem ela o cabo não se liga ao
     * bloco. Quem responde é o nó do Link (a metade de cima não tem block entity, então não liga cabo).
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ModBlockEntities.CLOUD_LINK.get(),
                (be, ctx) -> be.network() instanceof CloudLinkNode node ? node : null);
    }
}
