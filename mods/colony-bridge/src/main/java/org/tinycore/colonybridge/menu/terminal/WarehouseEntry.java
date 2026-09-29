package org.tinycore.colonybridge.menu.terminal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Uma entrada da grade do Terminal do Armazém: o tipo de item (stack de 1 unidade, com os componentes) e
 * quanto existe dele somando todos os racks. {@code count == 0} numa atualização = o item saiu do armazém.
 * <p>
 * {@code STREAM_CODEC} diz como a entrada vira bytes no pacote (≈ um serializador binário em C#).
 */
public record WarehouseEntry(ItemStack item, long count) {

    public static final StreamCodec<RegistryFriendlyByteBuf, WarehouseEntry> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, WarehouseEntry::item,
            ByteBufCodecs.VAR_LONG, WarehouseEntry::count,
            WarehouseEntry::new);
}
