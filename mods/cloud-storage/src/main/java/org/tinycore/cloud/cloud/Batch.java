package org.tinycore.cloud.cloud;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lote de operações duráveis de um jogador, a unidade que o servidor de jogo envia ao TCMine.
 *
 * <p>Idempotência: o TCMine aplica cada (jogador, época, seq) uma vez só; reenviar o mesmo lote depois
 * de um crash devolve "já aplicado". A época vem do lease (ver {@link PlayerCloudSession}) e impede que
 * um servidor com lease vencido sobrescreva o que outro fez.
 *
 * @param playerUuid UUID Minecraft do dono dos canais
 * @param epoch      época do lease em que o lote foi criado
 * @param seq        sequência dentro da época, começando em 1, sem buracos
 * @param ops        operações (uma por chave)
 * @param expected   saldo durável esperado de cada chave tocada DEPOIS do lote; o TCMine compara com o
 *                   que ele calcula e manda para a quarentena se divergir (detecta bug ou trapaça cedo)
 */
public record Batch(@NotNull UUID playerUuid, long epoch, long seq,
                    @NotNull List<CloudOp> ops, @NotNull Map<BalanceKey, Long> expected) {

    public Batch {
        if (seq < 1) throw new IllegalArgumentException("seq começa em 1");
        if (ops.isEmpty()) throw new IllegalArgumentException("lote vazio");
        ops = List.copyOf(ops);
        expected = Map.copyOf(expected);
    }
}
