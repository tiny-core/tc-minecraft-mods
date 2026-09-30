package org.tinycore.colonybridge.logic.loader;

/**
 * Regra pura do Chunk Loader (só números e flags, testada sem o jogo): em que situação ({@link LoaderState}) ele
 * está e quanto falta para soltar a área.
 * <p>
 * A contagem é em <b>tempo real</b> (milissegundos do relógio do servidor), a partir da última vez que um membro
 * da colônia foi visto online. Assim ela continua valendo depois de reiniciar o servidor.
 */
public final class LoaderRule {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private LoaderRule() {}

    /**
     * @param adminEnabled    config do servidor ({@code chunkLoaderEnabled})
     * @param switchedOn      o jogador deixou ligado (botão) e a redstone permite
     * @param validColony     dentro de uma colônia, com permissão e sem duplicata
     * @param powered         rede ME ativa (energia e canal)
     * @param inGrace         tolerância depois que um membro voltou (a rede ME pode estar na área descarregada)
     * @param memberOnline    algum membro da colônia está online
     * @param remainingMillis quanto falta da contagem ({@link #remainingMillis})
     */
    public static LoaderState state(boolean adminEnabled, boolean switchedOn, boolean validColony, boolean powered,
                                    boolean inGrace, boolean memberOnline, long remainingMillis) {
        if (!adminEnabled) {
            return LoaderState.DISABLED_BY_ADMIN;
        }
        if (!switchedOn) {
            return LoaderState.OFF;
        }
        if (!validColony) {
            return LoaderState.NO_COLONY;
        }
        if (!memberOnline && remainingMillis <= 0) {
            return LoaderState.SLEEPING;
        }
        if (!powered && !inGrace) {
            return LoaderState.NO_POWER;
        }
        return memberOnline ? LoaderState.LOADING : LoaderState.COUNTDOWN;
    }

    /**
     * Quanto falta para soltar a área.
     *
     * @param lastMemberMillis última vez que um membro foi visto online (-1 = nunca: conta como agora)
     * @param nowMillis        agora
     * @param offlineHours     config {@code chunkLoaderOfflineHours} (0 = solta na hora)
     * @return 0 se já acabou
     */
    public static long remainingMillis(long lastMemberMillis, long nowMillis, double offlineHours) {
        long since = lastMemberMillis < 0 ? 0 : Math.max(0, nowMillis - lastMemberMillis);
        long total = (long) (Math.max(0, offlineHours) * MILLIS_PER_HOUR);
        return Math.max(0, total - since);
    }

    /** Horas inteiras de uma duração (para "3 h 20 min"). */
    public static long hours(long millis) {
        return Math.max(0, millis) / MILLIS_PER_HOUR;
    }

    /** Minutos que sobram além das horas inteiras, arredondados para cima (nunca mostra "0 min" faltando tempo). */
    public static long minutes(long millis) {
        long rest = Math.max(0, millis) % MILLIS_PER_HOUR;
        return (rest + 59_999) / 60_000;
    }
}
