package org.tinycore.cloud.server;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Liga os eventos do jogo ao {@link CloudService}. Os momentos foram conferidos no código do NeoForge 21.1.252
 * (plano §11):
 * <ul>
 *   <li>{@code LevelEvent.Save} do overworld chega DEPOIS de os chunks irem para a fila de gravação;</li>
 *   <li>na parada, {@code ServerStoppingEvent} vem antes do {@code stopServer}, que salva os jogadores, salva o
 *       mundo com flush e só então descarrega os mundos ({@code LevelEvent.Unload}).</li>
 * </ul>
 */
public final class CloudServerEvents {

    private CloudServerEvents() {}

    /** Registra os ouvintes no barramento de eventos do jogo ({@code NeoForge.EVENT_BUS}). */
    public static void register(@NotNull IEventBus gameBus) {
        gameBus.addListener((ServerStartedEvent e) -> CloudService.start(e.getServer()));
        gameBus.addListener((ServerStoppingEvent e) -> with(CloudService::markStopping));
        gameBus.addListener((ServerStoppedEvent e) -> CloudService.stop());
        gameBus.addListener((ServerTickEvent.Post e) -> with(CloudService::tick));
        gameBus.addListener(CloudServerEvents::onSave);
        gameBus.addListener(CloudServerEvents::onUnload);
        gameBus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) with(s -> s.onLogin(player));
        });
        gameBus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) with(s -> s.onLogout(player));
        });
        gameBus.addListener((RegisterCommandsEvent e) -> CloudCommands.register(e.getDispatcher()));
    }

    private static void onSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            with(CloudService::onWorldSave);
        }
    }

    private static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
                && !level.getServer().isRunning()) {
            with(CloudService::onShutdownSaved);
        }
    }

    private static void with(java.util.function.Consumer<CloudService> action) {
        CloudService service = CloudService.get();
        if (service != null) action.accept(service);
    }
}
