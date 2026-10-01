package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado da nuvem de UM jogador enquanto este servidor segura o lease dele. Junta as três peças:
 * {@link ChannelBalances} (o que o jogador vê), {@link PendingChanges} (quando cada mudança fica
 * durável) e {@link BatchSequencer} (lotes numerados para o TCMine).
 *
 * <p>Quem chama (fases seguintes): o {@code MEStorage} do AE2 e a tela, em {@link #insert}/{@link #extract};
 * os eventos do servidor em {@link #endOfTick}, {@link #onWorldSave} e {@link #onFlushedSave}. Os lotes
 * devolvidos precisam ser gravados no diário ANTES de qualquer outra coisa acontecer no mundo, e só depois
 * enviados ao TCMine.
 *
 * <p>Somente leitura (canal congelado, incidente de rollback, TCMine fora do ar há tempo demais): nada
 * entra nem sai, mas o jogador continua vendo os saldos.
 */
public final class PlayerCloudSession {

    private final UUID playerUuid;
    private final ChannelBalances balances;
    private final PendingChanges pending = new PendingChanges();
    private final BatchSequencer sequencer;
    private boolean readOnly;

    /**
     * @param epoch    época do lease (o TCMine aumenta a cada acquire)
     * @param lastSeq  último seq já gerado nesta época (0 num lease novo)
     * @param snapshot saldos duráveis devolvidos pelo TCMine
     */
    public PlayerCloudSession(@NotNull UUID playerUuid, long epoch, long lastSeq, @NotNull Set<UUID> channelIds,
                              @NotNull Map<BalanceKey, Long> snapshot, @NotNull CloudQuota quota) {
        this.playerUuid = playerUuid;
        this.balances = new ChannelBalances(channelIds, snapshot, quota);
        this.sequencer = new BatchSequencer(playerUuid, epoch, lastSeq, snapshot);
    }

    /**
     * Coloca itens na nuvem (vindos do mundo). Mesmo contrato do AE2: devolve quanto foi (ou seria, com
     * {@code simulate}) aceito.
     */
    public long insert(@NotNull BalanceKey key, long amount, boolean simulate) {
        if (readOnly) return 0;
        long accepted = balances.insertable(key, amount);
        if (accepted == 0 || simulate) return accepted;
        balances.apply(key, accepted);
        pending.credit(key, accepted);
        return accepted;
    }

    /** Tira itens da nuvem (para o mundo). Devolve quanto foi (ou seria) retirado. */
    public long extract(@NotNull BalanceKey key, long amount, boolean simulate) {
        if (readOnly) return 0;
        long taken = balances.extractable(key, amount);
        if (taken == 0 || simulate) return taken;
        balances.apply(key, -taken);
        pending.debit(key, taken);
        return taken;
    }

    public long available(@NotNull BalanceKey key) {
        return balances.get(key);
    }

    /** Fim do tick do servidor: lote com os débitos do tick (vazio se não houve retirada). */
    public @NotNull List<Batch> endOfTick() {
        return sealEach(pending.drainTickDebits());
    }

    /**
     * Autosave (sem flush). Gera até dois lotes, nesta ordem: débitos ainda não gravados e créditos que
     * esperavam o IO do save anterior. Separados para o diário saber o que é débito recente (operação em
     * dúvida após um crash).
     */
    public @NotNull List<Batch> onWorldSave() {
        List<Batch> batches = new ArrayList<>(endOfTick());
        batches.addAll(sealEach(pending.onWorldSave()));
        return batches;
    }

    /** Save com flush, chamado DEPOIS da espera de IO (logout, parada, {@code /tccloud checkpoint}). */
    public @NotNull List<Batch> onFlushedSave() {
        List<Batch> batches = new ArrayList<>(endOfTick());
        batches.addAll(sealEach(pending.onFlushedSave()));
        return batches;
    }

    /** Créditos ainda não duráveis (para o diário registrar como "não confirmados"). */
    public @NotNull Map<BalanceKey, Long> pendingCredits() {
        return pending.pendingCredits();
    }

    /** Pode liberar o lease? Só sem nada pendente (depois de um {@link #onFlushedSave}). */
    public boolean isSettled() {
        return pending.isEmpty();
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public @NotNull UUID playerUuid() {
        return playerUuid;
    }

    public @NotNull ChannelBalances balances() {
        return balances;
    }

    public long epoch() {
        return sequencer.epoch();
    }

    public long lastSeq() {
        return sequencer.lastSeq();
    }

    private List<Batch> sealEach(List<CloudOp> ops) {
        return sequencer.seal(ops).map(List::of).orElse(List.of());
    }
}
