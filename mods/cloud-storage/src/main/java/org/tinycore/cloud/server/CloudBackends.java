package org.tinycore.cloud.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.integration.tcmine.CloudBackend;
import org.tinycore.cloud.integration.tcmine.CloudCredentials;
import org.tinycore.cloud.integration.tcmine.HttpCloudBackend;
import org.tinycore.cloud.integration.tcmine.LocalCloudBackend;

import java.nio.file.Path;

/**
 * Escolhe o backend da nuvem ao ligar o servidor:
 * <ol>
 *   <li>credenciais do TCMine ({@link CloudCredentials}: variáveis de ambiente ou {@code tccloud-server.json}) →
 *       {@link HttpCloudBackend}; exige {@code online-mode}, senão o UUID do jogador não é verificado pela
 *       Mojang e qualquer um entraria como outro jogador para levar os itens dele;</li>
 *   <li>{@code localCloud=true} (padrão): nuvem local, um arquivo deste computador ({@link LocalCloudFile});</li>
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
        if (Config.LOCAL_CLOUD.get()) {
            return local(server);
        }
        return null;
    }

    /**
     * Nuvem local (singleplayer, servidor sem TCMine). Em servidor dedicado exige {@code online-mode}, pela mesma
     * razão do TCMine; no singleplayer (e LAN aberta por ele) o próprio jogo autentica os jogadores. No ambiente de
     * desenvolvimento ({@code runServer}, sempre offline) a checagem é pulada para dar para testar.
     */
    private static @Nullable CloudBackend local(@NotNull MinecraftServer server) {
        if (server.isDedicatedServer() && !server.usesAuthentication() && FMLEnvironment.production) {
            TcCloud.LOG.error("Nuvem local DESLIGADA: o servidor está em online-mode=false. Sem a verificação da Mojang, "
                    + "qualquer um poderia entrar com o nome de outro jogador e levar os itens dele.");
            return null;
        }
        Path gameDir = server.getServerDirectory();
        Path file = LocalCloudFile.resolve(Config.LOCAL_CLOUD_FILE.get(), gameDir);
        LocalCloudFile.migrateLegacy(file, gameDir);
        try {
            return new LocalCloudBackend(file, () -> Config.LOCAL_LEASE_TTL_SECONDS.get() * 1000L,
                    CloudBackends::localQuota);
        } catch (LocalCloudBackend.InUseException e) {
            TcCloud.LOG.error("Nuvem local DESLIGADA: {}. Feche a outra instância (ou use outro localCloudFile).",
                    e.getMessage());
            return null;
        }
    }

    /** Cota da nuvem local, da config (0 = sem limite), lida a cada {@code hello}. */
    private static CloudQuota localQuota() {
        int types = Config.LOCAL_QUOTA_MAX_TYPES.get();
        long total = Config.LOCAL_QUOTA_MAX_TOTAL.get();
        return new CloudQuota(types == 0 ? Integer.MAX_VALUE : types, total == 0 ? Long.MAX_VALUE : total);
    }

    private static String modVersion() {
        return ModList.get().getModContainerById(TcCloud.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
