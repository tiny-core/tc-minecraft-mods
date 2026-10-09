package org.tinycore.colonybridge.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlock;
import org.tinycore.colonybridge.block.encoder.PatternEncoderBlock;
import org.tinycore.colonybridge.block.loader.ColonyChunkLoaderBlock;
import org.tinycore.colonybridge.block.monitor.MonitorBlock;
import org.tinycore.colonybridge.block.supply.ColonySupplyBlock;
import org.tinycore.colonybridge.block.terminal.WarehouseTerminalBlock;

/**
 * Blocos do mod. {@code DeferredRegister} é uma lista de "coisas a registrar" que o NeoForge
 * preenche no momento certo da inicialização; {@code DeferredBlock} é a referência que só pode
 * ser lida ({@code get()}) depois disso.
 */
public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ColonyBridgeMod.MOD_ID);

    public static final DeferredBlock<ColonyBridgeBlock> COLONY_BRIDGE = BLOCKS.register("colony_bridge",
        () -> new ColonyBridgeBlock(BlockBehaviour.Properties.of()
            .strength(2.2f, 11f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<ColonySupplyBlock> COLONY_SUPPLY = BLOCKS.register("colony_supply",
        () -> new ColonySupplyBlock(BlockBehaviour.Properties.of()
            .strength(2.2f, 11f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<MonitorBlock> COLONY_MONITOR = BLOCKS.register("colony_monitor",
        () -> new MonitorBlock(BlockBehaviour.Properties.of()
            .strength(1.5f, 6f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final DeferredBlock<WarehouseTerminalBlock> WAREHOUSE_TERMINAL = BLOCKS.register("warehouse_terminal",
        () -> new WarehouseTerminalBlock(BlockBehaviour.Properties.of()
            .strength(2.2f, 11f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<ColonyChunkLoaderBlock> CHUNK_LOADER = BLOCKS.register("colony_chunk_loader",
        () -> new ColonyChunkLoaderBlock(BlockBehaviour.Properties.of()
            .strength(3.5f, 1200f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    public static final DeferredBlock<PatternEncoderBlock> PATTERN_ENCODER = BLOCKS.register("pattern_encoder",
        () -> new PatternEncoderBlock(BlockBehaviour.Properties.of()
            .strength(2.2f, 11f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
            .noOcclusion()));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
