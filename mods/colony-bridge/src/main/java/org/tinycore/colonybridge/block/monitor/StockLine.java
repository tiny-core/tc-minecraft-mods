package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.supply.SupplyLineStatus;

/**
 * Uma linha do Abastecedor no monitor: item, tipo da linha, meta, quanto há no armazém e na rede ME e a
 * situação em palavras ({@link SupplyLineStatus}), tudo medido no último ciclo do Abastecedor.
 *
 * @param keep      true = "manter no armazém" (meta mínima, vem do ME); false = "excedente" (limite, volta ao ME)
 * @param target    quantidade configurada (mínimo ou máximo, conforme {@code keep})
 * @param warehouse quanto havia no armazém depois do último ciclo
 * @param network   quanto havia na rede ME
 */
public record StockLine(Item item, boolean keep, int target, long warehouse, long network, SupplyLineStatus status) {

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putBoolean("keep", keep);
        tag.putInt("target", target);
        tag.putLong("current", warehouse);
        tag.putLong("network", network);
        tag.putByte("status", (byte) status.ordinal());
        return tag;
    }

    /** null se o item não existe (mod removido): a linha é descartada. */
    static @Nullable StockLine load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? null
                : new StockLine(item, tag.getBoolean("keep"), tag.getInt("target"), tag.getLong("current"),
                tag.getLong("network"), SupplyLineStatus.byId(tag.getByte("status")));
    }
}
