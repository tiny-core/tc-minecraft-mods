package org.tinycore.colonybridge.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.block.monitor.MonitorBlockEntity;
import org.tinycore.colonybridge.block.supply.ColonySupplyBlockEntity;

/**
 * Tipos de block entity. Um {@code BlockEntityType} liga o construtor do block entity aos blocos
 * que podem tê-lo. O {@code build(null)} é o padrão do Minecraft (o parâmetro é de data fixer).
 */
public final class ModBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ColonyBridgeMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColonyBridgeBlockEntity>> COLONY_BRIDGE =
            BLOCK_ENTITIES.register("colony_bridge",
                    () -> BlockEntityType.Builder.of(ColonyBridgeBlockEntity::new, ModBlocks.COLONY_BRIDGE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ColonySupplyBlockEntity>> COLONY_SUPPLY =
            BLOCK_ENTITIES.register("colony_supply",
                    () -> BlockEntityType.Builder.of(ColonySupplyBlockEntity::new, ModBlocks.COLONY_SUPPLY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitorBlockEntity>> COLONY_MONITOR =
            BLOCK_ENTITIES.register("colony_monitor",
                    () -> BlockEntityType.Builder.of(MonitorBlockEntity::new, ModBlocks.COLONY_MONITOR.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
