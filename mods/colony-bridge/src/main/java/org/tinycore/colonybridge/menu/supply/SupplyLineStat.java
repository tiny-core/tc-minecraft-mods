package org.tinycore.colonybridge.menu.supply;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.logic.supply.SupplyLineStatus;

/**
 * O que a tela do Abastecedor mostra de cada linha além da configuração: quanto há no armazém e a situação
 * (cor e texto da linha). A configuração (alvo e quantidade) vai à parte, no {@code TargetListPayload}.
 */
public record SupplyLineStat(long warehouse, SupplyLineStatus status) {

    public static final SupplyLineStat EMPTY = new SupplyLineStat(0, SupplyLineStatus.UNKNOWN);

    public static final StreamCodec<ByteBuf, SupplyLineStat> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SupplyLineStat::warehouse,
            ByteBufCodecs.idMapper(SupplyLineStatus::byId, SupplyLineStatus::ordinal), SupplyLineStat::status,
            SupplyLineStat::new);
}
