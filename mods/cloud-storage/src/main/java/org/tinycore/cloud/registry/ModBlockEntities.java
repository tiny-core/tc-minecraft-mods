package org.tinycore.cloud.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.tinycore.cloud.TcCloud;
import org.tinycore.cloud.block.CloudLinkBlockEntity;

/** Tipos de block entity. O {@code build(null)} é o padrão do Minecraft (o parâmetro é de data fixer). */
public final class ModBlockEntities {

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TcCloud.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CloudLinkBlockEntity>> CLOUD_LINK =
            BLOCK_ENTITIES.register("cloud_link",
                    () -> BlockEntityType.Builder.of(CloudLinkBlockEntity::new, ModBlocks.CLOUD_LINK.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
