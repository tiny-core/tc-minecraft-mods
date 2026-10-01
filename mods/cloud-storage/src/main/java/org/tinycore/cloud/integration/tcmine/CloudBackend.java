package org.tinycore.cloud.integration.tcmine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.cloud.Batch;
import org.tinycore.cloud.cloud.CloudQuota;
import org.tinycore.cloud.cloud.journal.Checkpoint;
import org.tinycore.cloud.cloud.journal.DoubtfulOperation;
import org.tinycore.cloud.item.EncodedItem;
import org.tinycore.cloud.item.policy.ItemPolicy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tudo o que o mod pede à nuvem (plano §8). Duas implementações: {@link FileCloudBackend} (arquivo local, para
 * desenvolver e testar sem o TCMine) e, na fase 4, a HTTP do TCMine. O resto do mod só conhece esta interface.
 *
 * <p>Todos os métodos são assíncronos ({@code CompletableFuture} ≈ {@code Task} em C#) e completam fora da
 * thread do servidor; quem chama volta para ela com {@code server.execute(...)}. Falha de rede = future com
 * exceção; quem chama tenta de novo depois.
 */
public interface CloudBackend {

    /** Boot: identifica o mundo (checkpoint) e recebe a configuração da nuvem. */
    @NotNull CompletableFuture<HelloReply> hello(@NotNull Checkpoint checkpoint);

    /** Login: tenta segurar o canal do jogador neste servidor. */
    @NotNull CompletableFuture<LeaseResult> acquire(@NotNull UUID playerUuid, @NotNull String playerName);

    /** Renova os leases deste servidor. */
    @NotNull CompletableFuture<Void> heartbeat(@NotNull Collection<HeldLease> leases);

    /**
     * Envia um lote já gravado no diário.
     *
     * @param definitions itens do lote que o backend pode não conhecer ainda (ele ignora os que já tem)
     */
    @NotNull CompletableFuture<BatchResult> submit(@NotNull Batch batch, @NotNull List<EncodedItem> definitions);

    /** Libera o canal (depois que todos os lotes da época foram confirmados). */
    @NotNull CompletableFuture<Void> release(@NotNull UUID playerUuid, long epoch, long lastSeq);

    /** Boot após crash: operações que podem ter se perdido, para o dono decidir. */
    @NotNull CompletableFuture<Void> reportDoubtful(@NotNull List<DoubtfulOperation> operations);

    /** Nome para o log ("arquivo local", "TCMine em ..."). */
    @NotNull String describe();

    /** Para o backend (fecha threads/arquivos). */
    void close();

    /** Configuração da nuvem recebida no {@link #hello}. */
    record HelloReply(@NotNull ItemPolicy policy, @NotNull CloudQuota quota, int maxItemBytes, boolean readOnly,
                      @Nullable String readOnlyReason) {}

    /** Um lease que este servidor segura (para o heartbeat). */
    record HeldLease(@NotNull UUID playerUuid, long epoch) {}

    /** Um canal do jogador como o backend o entrega no acquire. */
    record ChannelSnapshot(@NotNull UUID id, @NotNull String name, @NotNull Map<String, Long> amounts,
                           @NotNull Map<String, EncodedItem> items) {}

    /** Resposta do acquire. */
    sealed interface LeaseResult {
        /** Canal entregue: época nova, canais e saldos. {@code readOnly} = congelado ou incidente aberto. */
        record Granted(long epoch, @NotNull List<ChannelSnapshot> channels, boolean readOnly) implements LeaseResult {}

        /** Outro servidor está com o canal. */
        record Busy(@NotNull String holder) implements LeaseResult {}
    }

    /** Resposta do envio de um lote. Falha de rede não é resultado: é exceção no future. */
    enum BatchResult {
        /** Aplicado agora. */
        APPLIED,
        /** Já tinha sido aplicado (reenvio após crash). */
        DUPLICATE,
        /** Recusado e guardado para o dono decidir (época velha, saldo negativo, divergência). */
        QUARANTINED
    }
}
