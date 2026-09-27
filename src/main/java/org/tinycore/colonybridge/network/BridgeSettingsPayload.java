package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Pacote cliente → servidor: "quero estas configurações na ponte da tela aberta".
 * <p>
 * Não leva posição de bloco: o servidor usa a tela que <i>ele</i> sabe que o jogador tem aberta
 * ({@code containerId} só confirma qual é). Toda a validação está em {@link ModNetwork}.
 * O modo de redstone viaja como número e é validado/convertido no servidor.
 */
public record BridgeSettingsPayload(int containerId, boolean craftingEnabled, int redstoneMode)
        implements CustomPacketPayload {

    public static final Type<BridgeSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "bridge_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSettingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BridgeSettingsPayload::containerId,
                    ByteBufCodecs.BOOL, BridgeSettingsPayload::craftingEnabled,
                    ByteBufCodecs.VAR_INT, BridgeSettingsPayload::redstoneMode,
                    BridgeSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
