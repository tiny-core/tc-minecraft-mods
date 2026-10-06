package org.tinycore.cloud.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.integration.tcmine.CloudBackend;
import org.tinycore.cloud.integration.tcmine.CloudCredentials;
import org.tinycore.cloud.integration.tcmine.FileCloudBackend;
import org.tinycore.cloud.integration.tcmine.HttpCloudBackend;

/**
 * Escolhe o backend da nuvem ao ligar o servidor:
 * <ol>
 *   <li>credenciais do TCMine ({@link CloudCredentials}: variáveis de ambiente ou {@code tccloud-server.json}) →
 *       {@link HttpCloudBackend}; exige {@code online-mode}, senão o UUID do jogador não é verificado pela
 *       Mojang e qualquer um entraria como outro jogador para levar os itens dele;</li>
 *   <li>{@code devFileBackend=true}: arquivo local de desenvolvimento;</li>
 *   <li>nenhum: nuvem desligada.</li>
 * </ol>
 */
final class CloudBackends {

    private CloudBackends() {}

    static @Nullable CloudBackend create(@NotNull MinecraftServer server) {
        CloudCredentials credentials = CloudCredentials.find(System::getenv, server.getServerDirectory());
        if (credentials != null) {
            if (!server.usesAuthentication()) {
                TcCloud.LOG.error("Nuvem DESLIGADA: o servidor está em online-mode=false. Sem a verificação da Mojang, "
                        + "qualquer um poderia entrar com o nome de outro jogador e levar os itens dele.");
                return null;
            }
            TcCloud.LOG.info("Nuvem: credenciais de {}.", credentials.source());
            return new HttpCloudBackend(credentials, modVersion());
        }
        if (Config.DEV_FILE_BACKEND.get()) {
            return new FileCloudBackend(server.getServerDirectory().resolve("tccloud-dev-backend.json"),
                    () -> Config.DEV_LEASE_TTL_SECONDS.get() * 1000L, CloudBackends::devQuota);
        }
        return null;
    }

    /** Cota da nuvem de teste, da config (0 = sem limite), lida a cada {@code hello}. */
    private static CloudQuota devQuota() {
        int types = Config.DEV_QUOTA_MAX_TYPES.get();
        long total = Config.DEV_QUOTA_MAX_TOTAL.get();
        return new CloudQuota(types == 0 ? Integer.MAX_VALUE : types, total == 0 ? Long.MAX_VALUE : total);
    }

    private static String modVersion() {
        return ModList.get().getModContainerById(TcCloud.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
