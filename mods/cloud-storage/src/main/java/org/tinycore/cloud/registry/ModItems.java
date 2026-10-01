package org.tinycore.cloud.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.cloud.TcCloud;

/** Itens do mod (por enquanto, só o item do bloco). */
public final class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TcCloud.MOD_ID);

    public static final DeferredItem<BlockItem> CLOUD_LINK =
            ITEMS.registerSimpleBlockItem("cloud_link", ModBlocks.CLOUD_LINK, new Item.Properties());

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
