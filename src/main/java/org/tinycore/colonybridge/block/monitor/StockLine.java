package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Uma linha do Abastecedor no monitor: item, tipo da linha, quantidade alvo e quanto existe no armazém.
 *
 * @param keep    true = "manter no armazém" (alvo mínimo); false = "excedente para o ME" (alvo máximo)
 * @param target  quantidade alvo configurada
 * @param current quanto havia no armazém no último ciclo do Abastecedor
 */
public record StockLine(Item item, boolean keep, int target, long current) {

    /** Situação da linha, para a cor no monitor. */
    public enum State { BELOW, OK, ABOVE }

    /**
     * Manter: abaixo do alvo = falta repor. Excedente: acima do alvo = vai voltar para o ME.
     * Nos dois casos, "OK" quando não há nada a fazer.
     */
    public State state() {
        if (keep) {
            return current < target ? State.BELOW : State.OK;
        }
        return current > target ? State.ABOVE : State.OK;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putBoolean("keep", keep);
        tag.putInt("target", target);
        tag.putLong("current", current);
        return tag;
    }

    /** null se o item não existe (mod removido): a linha é descartada. */
    static @Nullable StockLine load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null ? null
                : new StockLine(item, tag.getBoolean("keep"), tag.getInt("target"), tag.getLong("current"));
    }
}
