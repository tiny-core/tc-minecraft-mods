package org.tinycore.colonybridge.client.terminal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.tinycore.colonybridge.menu.terminal.WarehouseEntry;
import org.tinycore.core.client.ui.ItemGrid;

/**
 * Ensina a grade genérica do core ({@link ItemGrid}) a ler uma {@link WarehouseEntry} do Terminal do
 * Armazém: nome, mod, quantidade e ícone. Todo item do armazém existe no jogo, então nunca há ícone
 * substituto nem entrada apagada.
 */
final class WarehouseEntryAdapter implements ItemGrid.Adapter<WarehouseEntry> {

    @Override
    public @NotNull String name(@NotNull WarehouseEntry entry) {
        return entry.item().getHoverName().getString();
    }

    @Override
    public @NotNull String modId(@NotNull WarehouseEntry entry) {
        return BuiltInRegistries.ITEM.getKey(entry.item().getItem()).getNamespace();
    }

    @Override
    public long amount(@NotNull WarehouseEntry entry) {
        return entry.count();
    }

    @Override
    public @NotNull ItemStack icon(@NotNull WarehouseEntry entry) {
        return entry.item();
    }
}
