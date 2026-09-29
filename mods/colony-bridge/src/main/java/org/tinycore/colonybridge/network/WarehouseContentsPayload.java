package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.terminal.WarehouseEntry;

import java.util.List;

/**
 * Pacote servidor → cliente com o conteúdo do armazém para o Terminal aberto. Leva só o que <b>mudou</b>
 * desde o último envio ({@code WarehouseSync}); {@code reset = true} manda o cliente esquecer tudo antes
 * (primeiro envio ou troca de colônia). {@code status} = estado do terminal ({@code BridgeStatus}), para o cabeçalho.
 * <p>
 * Um armazém grande pode ter milhares de tipos, então a lista é dividida em pacotes de até
 * {@link #MAX_ENTRIES} entradas; o codec recusa listas maiores (proteção contra pacote gigante).
 */
public record WarehouseContentsPayload(int containerId, boolean reset, int status, List<WarehouseEntry> entries)
        implements CustomPacketPayload {

    public static final int MAX_ENTRIES = 256;

    public static final Type<WarehouseContentsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "warehouse_contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseContentsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, WarehouseContentsPayload::containerId,
                    ByteBufCodecs.BOOL, WarehouseContentsPayload::reset,
                    ByteBufCodecs.VAR_INT, WarehouseContentsPayload::status,
                    WarehouseEntry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), WarehouseContentsPayload::entries,
                    WarehouseContentsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
