package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.bridge.CraftSettings;

/**
 * Pacote cliente → servidor: "quero estas preferências de craft na ponte da tela aberta" (preferência,
 * modo de mods e mods marcados). Como no {@link BridgeSettingsPayload}, não leva posição de bloco.
 * O codec já descarta ids de mod inválidos e limita o tamanho da lista ({@link CraftSettings#sanitized});
 * permissão e distância são conferidas em {@link ModNetwork}.
 */
public record CraftSettingsPayload(int containerId, CraftSettings settings) implements CustomPacketPayload {

    public static final Type<CraftSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "craft_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftSettingsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CraftSettingsPayload::containerId,
                    CraftSettings.STREAM_CODEC, CraftSettingsPayload::settings,
                    CraftSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
