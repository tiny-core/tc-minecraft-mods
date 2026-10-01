package org.tinycore.cloud.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.block.CloudLinkBlock;

/**
 * Blocos do mod. {@code DeferredRegister} é uma lista de "coisas a registrar" que o NeoForge preenche no momento
 * certo da inicialização; {@code DeferredBlock} é a referência que só pode ser lida ({@code get()}) depois disso.
 */
public final class ModBlocks {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TcCloud.MOD_ID);

    public static final DeferredBlock<CloudLinkBlock> CLOUD_LINK = BLOCKS.register("cloud_link",
            () -> new CloudLinkBlock(BlockBehaviour.Properties.of()
                    .strength(2.2f, 11f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()));

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
