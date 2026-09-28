package org.tinycore.colonybridge.logic.bridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Resumo do último ciclo em três números, para a aba "Geral" da tela da ponte. A lista completa, com o
 * motivo de cada pedido, fica nos monitores; aqui só o suficiente para saber se a ponte está dando conta.
 *
 * @param total     pedidos em aberto vistos no ciclo
 * @param served    entregues, aguardando courier ou já no armazém
 * @param crafting  com craft desta ponte em andamento
 */
public record RequestCounts(int total, int served, int crafting) {

    public static final RequestCounts EMPTY = new RequestCounts(0, 0, 0);

    public static final StreamCodec<ByteBuf, RequestCounts> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequestCounts::total,
            ByteBufCodecs.VAR_INT, RequestCounts::served,
            ByteBufCodecs.VAR_INT, RequestCounts::crafting,
            RequestCounts::new);

    /** Pedidos que a ponte não conseguiu atender (sem estoque, filtrados, racks cheios, na fila...). */
    public int pending() {
        return Math.max(0, total - served - crafting);
    }
}
