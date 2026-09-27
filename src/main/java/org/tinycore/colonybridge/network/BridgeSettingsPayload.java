package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.BridgeSettings;

/**
 * Pacote cliente → servidor: "quero estas configurações na ponte da tela aberta".
 * <p>
 * Não leva posição de bloco: o servidor usa a tela que <i>ele</i> sabe que o jogador tem aberta
 * ({@code containerId} só confirma qual é). Enums inválidos já viram o padrão no codec
 * ({@link BridgeSettings#STREAM_CODEC}); o resto da validação está em {@link ModNetwork}.
 */
public record BridgeSettingsPayload(int containerId, BridgeSettings settings) implements CustomPacketPayload {

    public static final Type<BridgeSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "bridge_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSettingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BridgeSettingsPayload::containerId,
                    BridgeSettings.STREAM_CODEC, BridgeSettingsPayload::settings,
                    BridgeSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
