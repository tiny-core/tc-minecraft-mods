package org.tinycore.cloud.server;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.integration.tcmine.CloudBackend;
import org.tinycore.cloud.integration.tcmine.FileCloudBackend;

/**
 * Escolhe o backend da nuvem ao ligar o servidor:
 * <ol>
 *   <li>variáveis de ambiente {@code TCMINE_CLOUD_URL}/{@code TCMINE_CLOUD_KEY} (servidor do TCMine): fase 4,
 *       ainda não implementado, então a nuvem fica desligada com aviso;</li>
 *   <li>{@code devFileBackend=true}: arquivo local de desenvolvimento;</li>
 *   <li>nenhum: nuvem desligada.</li>
 * </ol>
 */
final class CloudBackends {

    static final String ENV_URL = "TCMINE_CLOUD_URL";

    private CloudBackends() {}

    static @Nullable CloudBackend create(@NotNull MinecraftServer server) {
        if (System.getenv(ENV_URL) != null) {
            TcCloud.LOG.warn("Nuvem: {} definido, mas o backend do TCMine chega na fase 4. Nuvem desligada.", ENV_URL);
            return null;
        }
        if (Config.DEV_FILE_BACKEND.get()) {
            return new FileCloudBackend(server.getServerDirectory().resolve("tccloud-dev-backend.json"),
                    () -> Config.DEV_LEASE_TTL_SECONDS.get() * 1000L);
        }
        return null;
    }
}
