package org.tinycore.colonybridge.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.item.ColonyTabletItem;
import org.tinycore.colonybridge.item.LinkCardItem;

/** Itens do mod, incluindo os itens dos blocos (o que fica no inventário). */
public final class ModItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColonyBridgeMod.MOD_ID);

    public static final DeferredItem<BlockItem> COLONY_BRIDGE =
            ITEMS.registerSimpleBlockItem("colony_bridge", ModBlocks.COLONY_BRIDGE, new Item.Properties());

    public static final DeferredItem<BlockItem> COLONY_SUPPLY =
            ITEMS.registerSimpleBlockItem("colony_supply", ModBlocks.COLONY_SUPPLY, new Item.Properties());

    public static final DeferredItem<BlockItem> COLONY_MONITOR =
            ITEMS.registerSimpleBlockItem("colony_monitor", ModBlocks.COLONY_MONITOR, new Item.Properties());

    public static final DeferredItem<BlockItem> WAREHOUSE_TERMINAL =
            ITEMS.registerSimpleBlockItem("warehouse_terminal", ModBlocks.WAREHOUSE_TERMINAL, new Item.Properties());

    public static final DeferredItem<BlockItem> CHUNK_LOADER =
            ITEMS.registerSimpleBlockItem("colony_chunk_loader", ModBlocks.CHUNK_LOADER, new Item.Properties());

    public static final DeferredItem<ColonyTabletItem> COLONY_TABLET =
            ITEMS.registerItem("colony_tablet", ColonyTabletItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredItem<LinkCardItem> LINK_CARD =
            ITEMS.registerItem("link_card", LinkCardItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
