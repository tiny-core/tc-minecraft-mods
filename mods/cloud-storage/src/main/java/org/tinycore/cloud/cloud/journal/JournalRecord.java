package org.tinycore.cloud.cloud.journal;

import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.BalanceKey;
import org.tinycore.cloud.item.EncodedItem;

import java.util.Map;
import java.util.UUID;

/**
 * Um registro do diário ({@link JournalFile}). O diário é append-only: nada é editado no lugar, o estado
 * é reconstruído lendo tudo em ordem ({@link JournalReplay}).
 *
 * <p>{@code sealed interface ... permits} ≈ união fechada: só estes tipos implementam a interface, e o
 * compilador avisa se um {@code switch} esquecer algum (parecido com um enum que carrega dados).
 */
public sealed interface JournalRecord {

    /** Lote durável, ainda não confirmado pelo TCMine até aparecer um {@link Ack} correspondente. */
    record BatchWritten(@NotNull Batch batch) implements JournalRecord {}

    /**
     * Definição de um item (bytes, id, nome), gravada antes do primeiro lote que o usa. O TCMine precisa dela
     * para registrar um item novo; reenviada junto dos lotes não confirmados após um crash.
     */
    record ItemDefined(@NotNull EncodedItem item) implements JournalRecord {}

    /** O TCMine confirmou (aplicou ou já tinha aplicado) o lote. */
    record Ack(@NotNull UUID playerUuid, long epoch, long seq) implements JournalRecord {}

    /** Um save do mundo terminou de ser gravado. Débitos depois da última marca são "recentes". */
    record SaveMark(long timeMillis) implements JournalRecord {}

    /**
     * Créditos ainda não duráveis de um jogador nesse momento (substitui o registro anterior do mesmo
     * jogador). Após um crash, viram "operações em dúvida" para o dono decidir.
     */
    record PendingCredits(@NotNull UUID playerUuid, @NotNull Map<BalanceKey, Long> credits) implements JournalRecord {
        public PendingCredits {
            credits = Map.copyOf(credits);
        }
    }

    /** O servidor parou limpo (tudo durável). Sem esta marca no fim, o boot seguinte trata como crash. */
    record CleanShutdown(long timeMillis) implements JournalRecord {}
}
