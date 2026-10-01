package org.tinycore.cloud.client;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.item.ItemCompatibility;
import org.tinycore.cloud.menu.LinkEntry;
import org.tinycore.core.client.ui.ItemGrid;

/**
 * Ensina a grade do core a ler um {@link LinkEntry}: item sem ícone usa o substituto e o nome guardado; item que
 * não está {@code OK} neste servidor aparece apagado.
 */
final class LinkEntryAdapter implements ItemGrid.Adapter<LinkEntry> {

    @Override
    public @NotNull String name(@NotNull LinkEntry entry) {
        return entry.icon().isEmpty() ? entry.name() : entry.icon().getHoverName().getString();
    }

    @Override
    public @NotNull String modId(@NotNull LinkEntry entry) {
        int colon = entry.itemId().indexOf(':');
        return colon < 0 ? "minecraft" : entry.itemId().substring(0, colon);
    }

    @Override
    public long amount(@NotNull LinkEntry entry) {
        return entry.amount();
    }

    @Override
    public @Nullable ItemStack icon(@NotNull LinkEntry entry) {
        return entry.icon().isEmpty() ? null : entry.icon();
    }

    @Override
    public boolean dimmed(@NotNull LinkEntry entry) {
        return entry.status() != ItemCompatibility.OK.ordinal();
    }
}
