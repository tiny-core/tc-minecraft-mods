package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coração do anti-duplicação: decide QUANDO cada mudança de saldo pode virar durável ("débito cedo,
 * crédito tarde"; plano §5).
 *
 * <p>Por que existe: o Minecraft grava chunks no disco a qualquer momento (até 20 por tick, e ao
 * descarregar). Depois de um crash, o disco é uma mistura de momentos. Para nunca duplicar:
 * <ul>
 *   <li><b>débito</b> (item saiu da nuvem para o mundo): durável no fim do MESMO tick, antes que o chunk
 *       com o item chegue ao disco (a gravação de chunks é assíncrona);</li>
 *   <li><b>crédito</b> (item saiu do mundo para a nuvem): só fica durável quando o mundo SEM o item já
 *       está em disco. Créditos de antes de um autosave ficam "aguardando IO" e só viram duráveis no
 *       autosave seguinte; num save com flush (que espera o IO), viram duráveis na hora.</li>
 * </ul>
 * Pior caso de um crash: perda (o dono vê nas "operações em dúvida"), nunca duplicação.
 *
 * <p>Netting é <b>assimétrico</b> de propósito: um débito consome primeiro os créditos ainda pendentes do
 * mesmo item (o item voltou ao mundo antes de o crédito existir no banco; anular é seguro). O contrário
 * NÃO é feito: um crédito não anula um débito do mesmo tick, porque o chunk com o item retirado pode ter
 * ido para a fila de gravação entre as duas operações (anular aí duplicaria após um crash).
 *
 * <p>Não é thread-safe: usado só na thread do servidor.
 */
public final class PendingChanges {

    /** Créditos desde o último save do mundo. */
    private Map<BalanceKey, Long> openCredits = new LinkedHashMap<>();
    /** Créditos de antes do último save, esperando a gravação daquele save terminar. */
    private Map<BalanceKey, Long> awaitingIoCredits = new LinkedHashMap<>();
    /** Débitos deste tick (valores positivos), duráveis no fim do tick. */
    private final Map<BalanceKey, Long> tickDebits = new LinkedHashMap<>();

    public void credit(@NotNull BalanceKey key, long amount) {
        requirePositive(amount);
        openCredits.merge(key, amount, Long::sum);
    }

    public void debit(@NotNull BalanceKey key, long amount) {
        requirePositive(amount);
        long rest = consume(openCredits, key, amount);
        rest = consume(awaitingIoCredits, key, rest);
        if (rest > 0) tickDebits.merge(key, rest, Long::sum);
    }

    /** Fim do tick: débitos que precisam ser gravados agora (deltas negativos). */
    public @NotNull List<CloudOp> drainTickDebits() {
        List<CloudOp> ops = toOps(tickDebits, -1);
        tickDebits.clear();
        return ops;
    }

    /**
     * Autosave (sem flush): os créditos que esperavam o IO do save ANTERIOR viram duráveis; os abertos
     * passam a esperar o IO deste save.
     */
    public @NotNull List<CloudOp> onWorldSave() {
        List<CloudOp> ops = toOps(awaitingIoCredits, 1);
        awaitingIoCredits = openCredits;
        openCredits = new LinkedHashMap<>();
        return ops;
    }

    /** Save com flush, chamado DEPOIS da espera de IO: todo crédito pendente vira durável. */
    public @NotNull List<CloudOp> onFlushedSave() {
        List<CloudOp> ops = toOps(awaitingIoCredits, 1);
        ops.addAll(toOps(openCredits, 1));
        awaitingIoCredits = new LinkedHashMap<>();
        openCredits = new LinkedHashMap<>();
        return merge(ops);
    }

    /** Créditos ainda não duráveis, somados; vão para o diário como "não confirmados" (operações em dúvida). */
    public @NotNull Map<BalanceKey, Long> pendingCredits() {
        Map<BalanceKey, Long> all = new LinkedHashMap<>(awaitingIoCredits);
        openCredits.forEach((k, v) -> all.merge(k, v, Long::sum));
        return Collections.unmodifiableMap(all);
    }

    /** Créditos ainda não duráveis de uma chave (o saldo local já os inclui). */
    public long pendingCredit(@NotNull BalanceKey key) {
        return openCredits.getOrDefault(key, 0L) + awaitingIoCredits.getOrDefault(key, 0L);
    }

    /** Débitos deste tick ainda não gravados de uma chave. */
    public long tickDebit(@NotNull BalanceKey key) {
        return tickDebits.getOrDefault(key, 0L);
    }

    public boolean isEmpty() {
        return openCredits.isEmpty() && awaitingIoCredits.isEmpty() && tickDebits.isEmpty();
    }

    /** Tira até {@code amount} do mapa; devolve o que sobrou sem consumir. */
    private static long consume(Map<BalanceKey, Long> map, BalanceKey key, long amount) {
        if (amount == 0) return 0;
        long have = map.getOrDefault(key, 0L);
        long used = Math.min(have, amount);
        if (used == have) map.remove(key);
        else map.put(key, have - used);
        return amount - used;
    }

    private static List<CloudOp> toOps(Map<BalanceKey, Long> map, int sign) {
        List<CloudOp> ops = new ArrayList<>(map.size());
        map.forEach((k, v) -> ops.add(new CloudOp(k, sign * v)));
        return ops;
    }

    /** Junta operações da mesma chave (um item pode estar nos dois mapas de crédito). */
    private static List<CloudOp> merge(List<CloudOp> ops) {
        Map<BalanceKey, Long> sum = new LinkedHashMap<>();
        for (CloudOp op : ops) sum.merge(op.key(), op.delta(), Long::sum);
        return toOps(sum, 1);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("quantidade deve ser positiva: " + amount);
    }
}
