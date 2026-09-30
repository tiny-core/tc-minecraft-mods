package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.supply.SupplyLineStatus;
import org.tinycore.colonybridge.logic.target.TargetLine;
import org.tinycore.colonybridge.logic.target.TargetSpec;

/**
 * Uma linha do Abastecedor no monitor: o alvo (item, tag ou mod), tipo da linha, meta, quanto há no armazém
 * e na rede ME e a situação em palavras ({@link SupplyLineStatus}), tudo medido no último ciclo.
 * <p>
 * Só vão ids e números (nada de pilhas com componentes), para o pacote ficar pequeno. O ícone de uma linha de
 * tag ou mod é escolhido no cliente (alterna entre os itens), sem pacotes extras.
 *
 * @param spec      alvo da linha
 * @param item      item da linha de item ({@link Items#AIR} para tag, mod ou item que não existe mais)
 * @param keep      true = "manter no armazém" (meta mínima, vem do ME); false = "excedente" (limite, volta ao ME)
 * @param target    quantidade configurada (mínimo ou máximo, conforme {@code keep}); 0 com {@code all}
 * @param all       excedente "tudo" (devolve tudo)
 * @param warehouse quanto havia no armazém depois do último ciclo
 * @param network   quanto havia na rede ME
 */
public record StockLine(TargetSpec spec, Item item, boolean keep, int target, boolean all, long warehouse,
                        long network, SupplyLineStatus status) {

    public static StockLine of(TargetLine line, boolean keep, long warehouse, long network, SupplyLineStatus status) {
        Item item = line.item().isEmpty() ? Items.AIR : line.item().getItem();
        return new StockLine(line.spec(), item, keep, line.amount(), line.all(), warehouse, network, status);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("target", spec.text());
        if (item != Items.AIR) {
            tag.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        }
        tag.putBoolean("keep", keep);
        tag.putInt("amount", target);
        tag.putBoolean("all", all);
        tag.putLong("current", warehouse);
        tag.putLong("network", network);
        tag.putByte("status", (byte) status.ordinal());
        return tag;
    }

    /** null se o alvo é inválido (o dado vem da rede): a linha é descartada. */
    static @Nullable StockLine load(CompoundTag tag) {
        TargetSpec spec = TargetSpec.parse(tag.getString("target"));
        if (spec == null) {
            return null;
        }
        ResourceLocation id = tag.contains("id") ? ResourceLocation.tryParse(tag.getString("id")) : null;
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return new StockLine(spec, item, tag.getBoolean("keep"), tag.getInt("amount"), tag.getBoolean("all"),
                tag.getLong("current"), tag.getLong("network"), SupplyLineStatus.byId(tag.getByte("status")));
    }
}
