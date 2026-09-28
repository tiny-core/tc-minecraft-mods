package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.supply.StockList;

import java.util.List;

/**
 * Pacote cliente → servidor com a configuração inteira do bloco de abastecimento: modo de redstone e a
 * quantidade alvo de cada linha.
 * <p>
 * Manda tudo de uma vez em vez de uma mensagem por mudança: o pacote continua minúsculo (19 números) e
 * o resultado não depende da ordem de chegada. Os itens das linhas não vêm aqui — eles são slots do
 * menu, com o clique já validado. O servidor limita cada valor (ver {@link ModNetwork}).
 */
public record SupplyConfigPayload(int containerId, int redstoneMode, List<Integer> amounts)
        implements CustomPacketPayload {

    public static final Type<SupplyConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "supply_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplyConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SupplyConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT, SupplyConfigPayload::redstoneMode,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(StockList.SIZE)), SupplyConfigPayload::amounts,
                    SupplyConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
