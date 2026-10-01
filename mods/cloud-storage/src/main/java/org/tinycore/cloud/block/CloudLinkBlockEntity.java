package org.tinycore.cloud.block;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.integration.ae2.CloudLinkNode;
import org.tinycore.cloud.registry.ModBlockEntities;
import org.tinycore.cloud.server.CloudService;

import java.util.UUID;

/**
 * Estado do TC Cloud Link no mundo: o dono (quem colocou), o modo de acesso da rede, a prioridade e o nó AE2
 * ({@link CloudLinkNode}). Não guarda item nenhum: os itens estão na nuvem, então quebrar o bloco não derruba
 * nada.
 *
 * <p>Registra-se no {@code CloudListeners} do serviço para remontar o canal no AE2 quando o dono entra, sai ou
 * muda de situação. O registro é feito no tick, porque o block entity pode carregar antes de o serviço existir
 * (chunks de spawn carregam antes do {@code ServerStartedEvent}).
 */
public class CloudLinkBlockEntity extends BlockEntity implements IInWorldGridNodeHost, CloudLinkNode.Host {

    private final CloudLinkNode node;
    private @Nullable UUID owner;
    private String ownerName = "";
    private NetworkAccess access = NetworkAccess.FULL;
    private int priority;
    private boolean nodeCreated;
    private @Nullable CloudService listeningTo;
    private final Runnable onCloudChange = this::refreshMounts;
    /** Onde o canal já está montado quando este Link não conseguiu montá-lo (para a tela). */
    private @Nullable GlobalPos conflict;

    public CloudLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOUD_LINK.get(), pos, state);
        this.node = new CloudLinkNode(this);
    }

    /** Tick do servidor: cria o nó no primeiro tick e se registra no serviço quando ele existir. */
    public void serverTick() {
        if (level == null) return;
        if (!nodeCreated) {
            nodeCreated = true;
            node.create(level, worldPosition);
        }
        CloudService service = CloudService.get();
        if (service != listeningTo) {
            stopListening();
            if (service != null && owner != null) {
                service.listeners().add(owner, onCloudChange);
                listeningTo = service;
                refreshMounts();
            }
        }
    }

    private void refreshMounts() {
        node.refreshMounts();
    }

    private void stopListening() {
        if (listeningTo != null && owner != null) {
            listeningTo.listeners().remove(owner, onCloudChange);
            if (level != null) listeningTo.mounts().release(GlobalPos.of(level.dimension(), worldPosition));
        }
        listeningTo = null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        stopListening();
        node.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        stopListening();
        node.destroy();
    }

    // ---------------------------------------------------------------- dono e configuração

    public void setOwner(@NotNull Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        node.setOwner(player);
        setChanged();
    }

    public boolean isOwner(@NotNull Player player) {
        return player.getUUID().equals(owner);
    }

    public @NotNull String ownerName() {
        return ownerName;
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public @NotNull NetworkAccess access() {
        return access;
    }

    @Override
    public int priority() {
        return priority;
    }

    public void setAccess(@NotNull NetworkAccess access) {
        if (this.access == access) return;
        this.access = access;
        setChanged();
        refreshMounts();
    }

    public void setPriority(int priority) {
        if (this.priority == priority) return;
        this.priority = priority;
        setChanged();
        refreshMounts();
    }

    @Override
    public void onMountConflict(@Nullable GlobalPos holder) {
        conflict = holder;
    }

    public @Nullable GlobalPos mountConflict() {
        return conflict;
    }

    public boolean isNetworkActive() {
        return node.isActive();
    }

    // ---------------------------------------------------------------- AE2

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return node.gridNode();
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        node.save(tag);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putInt("access", access.ordinal());
        tag.putInt("priority", priority);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        node.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        access = NetworkAccess.byId(tag.getInt("access"));
        priority = tag.getInt("priority");
    }
}
