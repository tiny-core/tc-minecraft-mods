package org.tinycore.colonybridge.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.block.ColonyBridgeBlock;

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
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
