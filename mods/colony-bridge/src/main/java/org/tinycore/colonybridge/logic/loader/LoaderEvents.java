package org.tinycore.colonybridge.logic.loader;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.tinycore.colonybridge.block.loader.ColonyChunkLoaderBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.logic.colony.ColonyBlockRegistry;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;

/**
 * Jogador entrou ou saiu do servidor: acorda os Chunk Loaders para eles conferirem na hora se há membro da colônia
 * online (começar ou zerar a contagem), sem esperar o próximo passo de 1 s. Só percorre os loaders registrados
 * (um por colônia) e só os de chunk carregado — o do próprio loader fica carregado enquanto ele está ligado.
 * <p>
 * Registrado no barramento de eventos do jogo ({@code NeoForge.EVENT_BUS}) pelo {@code ColonyBridgeMod}.
 */
public final class LoaderEvents {

    private LoaderEvents() {}

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        wakeAll(event.getEntity());
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        wakeAll(event.getEntity());
    }

    private static void wakeAll(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ColonyBlockRegistry.get(server.overworld()).holders(ColonyBlockType.CHUNK_LOADER).forEach((colony, pos) -> {
            ResourceLocation dimension = ColonyAccess.dimensionOf(colony);
            ServerLevel level = dimension == null ? null
                    : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level != null && level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof ColonyChunkLoaderBlockEntity loader) {
                loader.wake();
            }
        });
    }
}
