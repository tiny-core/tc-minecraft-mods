package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Pacote cliente → servidor: um clique na grade do Terminal do Armazém. Leva só a ação
 * ({@code TerminalAction}) e o tipo de item clicado (vazio nas ações de guardar, que usam o item do cursor).
 * <p>
 * O cliente não diz quantidade nem de onde tirar: o servidor decide tudo a partir do que existe nos racks.
 */
public record WarehouseActionPayload(int containerId, int action, ItemStack item) implements CustomPacketPayload {

    public static final Type<WarehouseActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "warehouse_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, WarehouseActionPayload::containerId,
                    ByteBufCodecs.VAR_INT, WarehouseActionPayload::action,
                    ItemStack.OPTIONAL_STREAM_CODEC, WarehouseActionPayload::item,
                    WarehouseActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
