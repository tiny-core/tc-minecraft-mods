package org.tinycore.cloud.cloud;

/**
 * Cota de um canal, definida pelo dono da nuvem no painel do TCMine (o servidor de jogo só aplica).
 *
 * @param maxTypes quantos itens diferentes o canal aceita
 * @param maxTotal soma máxima de todas as quantidades do canal
 */
public record CloudQuota(int maxTypes, long maxTotal) {

    /** Sem limite prático; usado em testes e enquanto o TCMine não mandou a configuração. */
    public static final CloudQuota UNLIMITED = new CloudQuota(Integer.MAX_VALUE, Long.MAX_VALUE);

    public CloudQuota {
        if (maxTypes < 0 || maxTotal < 0) throw new IllegalArgumentException("cota negativa");
    }

    /**
     * Quanto de {@code amount} cabe no canal.
     *
     * @param isNewType o item ainda não está no canal (ocuparia mais um tipo)
     * @param types     tipos já presentes no canal
     * @param total     soma atual do canal
     */
    /** true se a cota limita o número de tipos. */
    public boolean limitsTypes() {
        return maxTypes != Integer.MAX_VALUE;
    }

    /** true se a cota limita a soma das quantidades. */
    public boolean limitsTotal() {
        return maxTotal != Long.MAX_VALUE;
    }

    /**
     * Quanto da cota está ocupado, de 0 a 1: o mais apertado entre tipos e total (é o que trava primeiro). Cota sem
     * limite nenhum = 0. Limite 0 conta como cheio.
     */
    public double fill(int types, long total) {
        double fill = 0;
        if (limitsTypes()) fill = Math.max(fill, ratio(types, maxTypes));
        if (limitsTotal()) fill = Math.max(fill, ratio(total, maxTotal));
        return fill;
    }

    private static double ratio(long used, long max) {
        if (max <= 0) return 1;
        return Math.max(0, Math.min(1, (double) used / max));
    }

    public long acceptable(boolean isNewType, int types, long total, long amount) {
        if (isNewType && types >= maxTypes) return 0;
        long room = maxTotal - total;
        return Math.max(0, Math.min(amount, room));
    }
}
