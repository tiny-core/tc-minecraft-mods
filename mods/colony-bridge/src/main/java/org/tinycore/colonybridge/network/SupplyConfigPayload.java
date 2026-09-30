package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;


/**
 * Pacote cliente → servidor com a configuração geral do Abastecedor: o modo de redstone. As linhas das listas
 * vão pelo {@link TargetEditPayload}, uma edição por mensagem.
 */
public record SupplyConfigPayload(int containerId, int redstoneMode)
        implements CustomPacketPayload {

    public static final Type<SupplyConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "supply_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplyConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SupplyConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT, SupplyConfigPayload::redstoneMode,
                    SupplyConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
