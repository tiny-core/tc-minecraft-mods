package org.tinycore.colonybridge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.ColonyBridgeBlock;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;

public final class ModRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ColonyBridgeMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ColonyBridgeMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ColonyBridgeMod.MOD_ID);

    public static final DeferredBlock<ColonyBridgeBlock> COLONY_BRIDGE = BLOCKS.register("colony_bridge",
            () -> new ColonyBridgeBlock(BlockBehaviour.Properties.of()
                    .strength(2.2f, 11f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> COLONY_BRIDGE_ITEM =
            ITEMS.registerSimpleBlockItem("colony_bridge", COLONY_BRIDGE, new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColonyBridgeBlockEntity>> COLONY_BRIDGE_BE =
            BLOCK_ENTITIES.register("colony_bridge",
                    () -> BlockEntityType.Builder.of(ColonyBridgeBlockEntity::new, COLONY_BRIDGE.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private ModRegistries() {}
}
