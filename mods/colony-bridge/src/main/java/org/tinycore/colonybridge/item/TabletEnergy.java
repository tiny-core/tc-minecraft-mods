package org.tinycore.colonybridge.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.registry.ModDataComponents;

/**
 * Bateria do tablet: a energia fica no componente {@code TABLET_ENERGY} do item e é lida/escrita pela
 * {@link ComponentEnergyStorage} do NeoForge. A mesma bateria é exposta como capability de energia
 * ({@code ColonyBridgeMod}), então carregadores de outros mods (Mekanism, Flux Networks...) também carregam.
 * <p>
 * A capacidade vem da config do servidor; antes de ela carregar (menu principal, JEI) vale o padrão.
 */
public final class TabletEnergy {

    private static final int DEFAULT_CAPACITY = 100_000;

    private TabletEnergy() {}

    public static int capacity() {
        return Config.SPEC.isLoaded() ? Config.TABLET_CAPACITY.get() : DEFAULT_CAPACITY;
    }

    /** Bateria do item (objeto leve, criado a cada uso: o dado mora no próprio item). */
    public static IEnergyStorage storage(ItemStack stack) {
        int capacity = capacity();
        return new ComponentEnergyStorage(stack, ModDataComponents.TABLET_ENERGY.get(), capacity, capacity, capacity);
    }

    public static int stored(ItemStack stack) {
        return Math.min(stack.getOrDefault(ModDataComponents.TABLET_ENERGY.get(), 0), capacity());
    }
}
