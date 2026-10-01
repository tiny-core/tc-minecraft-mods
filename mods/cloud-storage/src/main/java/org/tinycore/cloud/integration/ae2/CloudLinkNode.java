package org.tinycore.cloud.integration.ae2;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.Config;
import org.tinycore.cloud.block.NetworkAccess;
import org.tinycore.cloud.server.CloudService;

import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A parte AE2 do TC Cloud Link: o nó da grade e o {@code IStorageProvider} que monta o canal na rede. Isolada
 * aqui para o block entity não importar nada do AE2 além deste pacote (uma atualização do AE2 quebra um pacote
 * só).
 *
 * <p>O AE2 pergunta "o que você monta?" em {@link #mountInventories}; quando algo muda (dono entrou/saiu, modo
 * trocou), {@link #refreshMounts} pede para ele perguntar de novo.
 */
public final class CloudLinkNode implements IStorageProvider {

    /** O que o nó precisa saber do block entity dono. */
    public interface Host {
        @Nullable UUID owner();

        @NotNull NetworkAccess access();

        int priority();

        /** O canal deste Link não pôde ser montado (já está em outro Link) ou voltou a poder. */
        void onMountConflict(@Nullable GlobalPos holder);
    }

    private final IManagedGridNode node;
    private final Host host;
    private @Nullable CloudMEStorage storage;
    private @Nullable UUID storageChannel;

    public <T extends BlockEntity & Host> CloudLinkNode(@NotNull T blockEntity) {
        this.host = blockEntity;
        this.node = GridHelper.createManagedNode(blockEntity, new Listener<T>())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setInWorldNode(true)
                .setTagName("node")
                .addService(IStorageProvider.class, this);
    }

    /** Cria o nó no mundo (primeiro tick do block entity, só no servidor). */
    public void create(@NotNull Level level, @NotNull BlockPos pos) {
        node.setIdlePowerUsage(Config.LINK_IDLE_POWER.get());
        node.create(level, pos);
    }

    public void destroy() {
        node.destroy();
    }

    public void setOwner(@NotNull Player player) {
        node.setOwningPlayer(player);
    }

    public @Nullable IGridNode gridNode() {
        return node.getNode();
    }

    public boolean isActive() {
        return node.isActive();
    }

    /** Pede ao AE2 para montar de novo (dono entrou/saiu, modo ou prioridade mudou). */
    public void refreshMounts() {
        IStorageProvider.requestUpdate(node);
    }

    public void save(@NotNull CompoundTag tag) {
        node.saveToNBT(tag);
    }

    public void load(@NotNull CompoundTag tag) {
        node.loadFromNBT(tag);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        CloudService service = CloudService.get();
        UUID owner = host.owner();
        if (service == null || owner == null || !host.access().mounts() || service.session(owner) == null) return;
        UUID channel = service.defaultChannel(owner);
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
