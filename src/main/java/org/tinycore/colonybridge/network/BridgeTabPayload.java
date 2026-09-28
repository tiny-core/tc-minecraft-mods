package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Pacote cliente → servidor: "abri esta aba na tela da ponte". Só serve para o shift-clique do
 * inventário ir para o grupo certo de ghost slots (filtro ou preferidos). Não muda nada no bloco; um
 * número inválido vira a aba "Geral" ({@code BridgeTab.byId}).
 */
public record BridgeTabPayload(int containerId, int tab) implements CustomPacketPayload {

    public static final Type<BridgeTabPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "bridge_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeTabPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BridgeTabPayload::containerId,
                    ByteBufCodecs.VAR_INT, BridgeTabPayload::tab,
                    BridgeTabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
