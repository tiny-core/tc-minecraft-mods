package org.tinycore.cloud.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.menu.CloudLinkMenu;

/**
 * Tipos de menu. {@code IMenuTypeExtension.create} permite ao cliente receber dados extras na abertura (aqui, a
 * posição do bloco).
 */
public final class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, TcCloud.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CloudLinkMenu>> CLOUD_LINK =
            MENUS.register("cloud_link", () -> IMenuTypeExtension.create(CloudLinkMenu::new));

    private ModMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
