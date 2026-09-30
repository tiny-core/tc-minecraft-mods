package org.tinycore.colonybridge.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Servidor → cliente: os dados de painel (os mesmos do Monitor da Colônia, {@code MonitorData.save}) para a aba de
 * painel do tablet aberta. Vai como {@code CompoundTag} porque o monitor já sabe salvar/ler nesse formato, com os
 * limites de tamanho de listas aplicados na leitura ({@code MonitorData.load}).
 */
public record TabletPanelPayload(int containerId, CompoundTag data) implements CustomPacketPayload {

    public static final Type<TabletPanelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "tablet_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TabletPanelPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TabletPanelPayload::containerId,
                    ByteBufCodecs.COMPOUND_TAG, TabletPanelPayload::data,
                    TabletPanelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
