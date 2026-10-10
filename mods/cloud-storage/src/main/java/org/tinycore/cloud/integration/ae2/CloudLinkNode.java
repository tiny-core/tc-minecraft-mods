package org.tinycore.cloud.integration.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.block.LinkHost;
import org.tinycore.cloud.block.LinkNetwork;
import org.tinycore.cloud.server.CloudService;

import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A parte AE2 do TC Cloud Link: o nó da grade e o {@code IStorageProvider} que monta o canal na rede. É a
 * {@link LinkNetwork} do Link quando o AE2 está instalado (criada pelo {@link Ae2Bridge}); o block entity não
 * importa nada do AE2, e por isso o mod também roda sem ele. O nó não gasta energia (consumo parado 0).
 *
 * <p>O AE2 pergunta "o que você monta?" em {@link #mountInventories}; quando algo muda (dono entrou/saiu, modo
 * trocou), {@link #refreshMounts} pede para ele perguntar de novo.
 */
public final class CloudLinkNode implements IStorageProvider, LinkNetwork, IInWorldGridNodeHost {

    private final IManagedGridNode node;
    private final LinkHost host;
    private @Nullable CloudMEStorage storage;
    private @Nullable UUID storageChannel;

    public <T extends BlockEntity & LinkHost> CloudLinkNode(@NotNull T blockEntity) {
        this.host = blockEntity;
        this.node = GridHelper.createManagedNode(blockEntity, new Listener<T>())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setInWorldNode(true)
                .setTagName("node")
                .addService(IStorageProvider.class, this);
    }

    /** Cria o nó no mundo (primeiro tick do block entity, só no servidor). */
    @Override
    public void create(@NotNull Level level, @NotNull BlockPos pos) {
        node.setIdlePowerUsage(0); // o Link não gasta energia da rede
        node.create(level, pos);
    }

    @Override
    public void destroy() {
        node.destroy();
    }

    @Override
    public void setOwner(@NotNull Player player) {
        node.setOwningPlayer(player);
    }

    public @Nullable IGridNode gridNode() {
        return node.getNode();
    }

    /** O AE2 acha o nó por aqui (capability {@code IN_WORLD_GRID_NODE_HOST}, ver {@link Ae2Bridge}). */
    @Override
    public @Nullable IGridNode getGridNode(@NotNull Direction dir) {
        return node.getNode();
    }

    @Override
    public boolean isActive() {
        return node.isActive();
    }

    /** Pede ao AE2 para montar de novo (dono entrou/saiu, modo ou prioridade mudou). */
    @Override
    public void refreshMounts() {
        IStorageProvider.requestUpdate(node);
    }

    @Override
    public void save(@NotNull CompoundTag tag) {
        node.saveToNBT(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        node.loadFromNBT(tag);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        CloudService service = CloudService.get();
        UUID owner = host.owner();
        if (service == null || owner == null || !host.access().mounts() || service.session(owner) == null) return;
        UUID channel = service.channelFor(owner, host.channel());
        if (channel == null) return;
        BlockEntity be = (BlockEntity) host;
        if (be.getLevel() == null) return;
        GlobalPos here = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
        if (!service.mounts().claim(channel, here)) {
            host.onMountConflict(service.mounts().holder(channel));
            return;
        }
        host.onMountConflict(null);
        if (storage == null || !channel.equals(storageChannel)) {
            storage = new CloudMEStorage(owner, channel, host::access);
            storageChannel = channel;
        }
        mounts.mount(storage, host.priority());
    }

    private static final class Listener<T extends BlockEntity> implements IGridNodeListener<T> {
        @Override
        public void onSaveChanges(T nodeOwner, IGridNode node) {
            nodeOwner.setChanged();
        }
    }
}
