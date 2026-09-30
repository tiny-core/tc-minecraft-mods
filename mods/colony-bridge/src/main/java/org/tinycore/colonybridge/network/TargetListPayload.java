package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.menu.TargetLineView;

import java.util.List;

/**
 * Servidor → cliente: as linhas de uma lista (Manter, Excedente ou filtro) da tela aberta. Enviado ao abrir
 * a tela e só quando a lista muda ({@code TargetListSync}), não a cada segundo.
 *
 * @param kind ordinal do {@code TargetListKind}
 */
public record TargetListPayload(int containerId, int kind, List<TargetLineView> lines) implements CustomPacketPayload {

    public static final Type<TargetListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "target_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TargetListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TargetListPayload::containerId,
                    ByteBufCodecs.VAR_INT, TargetListPayload::kind,
                    TargetLineView.STREAM_CODEC.apply(ByteBufCodecs.list(TargetList.HARD_MAX_LINES)),
                    TargetListPayload::lines,
                    TargetListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
