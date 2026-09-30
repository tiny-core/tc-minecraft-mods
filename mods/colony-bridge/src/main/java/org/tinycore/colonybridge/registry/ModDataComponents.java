package org.tinycore.colonybridge.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.item.TabletLink;

/**
 * Tipos de "data component" do mod: dados guardados dentro de um item (no 1.21 os itens não têm mais NBT solto;
 * cada dado é um componente com tipo, parecido com propriedades tipadas de um objeto em C#).
 * {@code persistent} = salvo no mundo; {@code networkSynchronized} = enviado ao cliente (dica, barra de energia).
 */
public final class ModDataComponents {

    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ColonyBridgeMod.MOD_ID);

    /** Energia do tablet em FE (lida pela {@code ComponentEnergyStorage} do NeoForge). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TABLET_ENERGY =
            COMPONENTS.registerComponentType("tablet_energy", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Colônia à qual o tablet está ligado. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TabletLink>> TABLET_LINK =
            COMPONENTS.registerComponentType("tablet_link", builder -> builder
                    .persistent(TabletLink.CODEC)
                    .networkSynchronized(TabletLink.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
