package org.tinycore.colonybridge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.menu.ColonyBridgeMenu;
import org.tinycore.colonybridge.menu.ColonySupplyMenu;

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

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
