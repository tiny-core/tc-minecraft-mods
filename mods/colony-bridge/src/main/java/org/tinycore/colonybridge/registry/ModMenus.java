package org.tinycore.colonybridge.registry;

import org.tinycore.colonybridge.menu.encoder.PatternEncoderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderMenu;
import org.tinycore.colonybridge.menu.tablet.TabletPanelMenu;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;

/**
 * Tipos de menu (telas ligadas a blocos). O {@code MenuType} diz ao cliente qual construtor usar
 * quando o servidor manda abrir uma tela; {@code IMenuTypeExtension.create} permite receber os
 * "dados extras" da abertura (aqui, a posição do bloco).
 */
public final class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ColonyBridgeMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ColonyBridgeMenu>> COLONY_BRIDGE =
            MENUS.register("colony_bridge", () -> IMenuTypeExtension.create(ColonyBridgeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ColonySupplyMenu>> COLONY_SUPPLY =
            MENUS.register("colony_supply", () -> IMenuTypeExtension.create(ColonySupplyMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<WarehouseTerminalMenu>> WAREHOUSE_TERMINAL =
            MENUS.register("warehouse_terminal", () -> IMenuTypeExtension.create(WarehouseTerminalMenu::new));

    /** Aba de painel do tablet (dados do monitor de um bloco, sem monitor). */
    public static final DeferredHolder<MenuType<?>, MenuType<TabletPanelMenu>> TABLET_PANEL =
            MENUS.register("tablet_panel", () -> IMenuTypeExtension.create(TabletPanelMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ChunkLoaderMenu>> CHUNK_LOADER =
            MENUS.register("colony_chunk_loader", () -> IMenuTypeExtension.create(ChunkLoaderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<PatternEncoderMenu>> PATTERN_ENCODER =
            MENUS.register("pattern_encoder", () -> IMenuTypeExtension.create(PatternEncoderMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
