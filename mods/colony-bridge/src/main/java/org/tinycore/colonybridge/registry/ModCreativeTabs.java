package org.tinycore.colonybridge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Aba própria do mod no modo criativo.
 * <p>
 * Abas do criativo são um registro como blocos e itens, por isso usam {@code DeferredRegister}
 * (lista de "coisas a registrar" que o NeoForge preenche no momento certo da inicialização).
 * Para aparecer na aba, um item novo precisa ser adicionado em {@code displayItems}.
 */
public final class ModCreativeTabs {

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ColonyBridgeMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tccolonybridge"))
                    .icon(() -> new ItemStack(ModItems.COLONY_BRIDGE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.COLONY_BRIDGE.get());
                        output.accept(ModItems.COLONY_SUPPLY.get());
                        output.accept(ModItems.COLONY_MONITOR.get());
                        output.accept(ModItems.WAREHOUSE_TERMINAL.get());
                        output.accept(ModItems.LINK_CARD.get());
                        output.accept(ModItems.CHUNK_LOADER.get());
                        output.accept(ModItems.COLONY_TABLET.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
