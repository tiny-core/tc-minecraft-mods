package org.tinycore.cloud.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.integration.Ae2Compat;
import org.tinycore.cloud.registry.ModBlockEntities;
import org.tinycore.cloud.server.CloudService;

import java.util.UUID;

/**
 * Estado do TC Cloud Link no mundo: o dono (quem colocou), o canal escolhido, o modo de acesso da rede, a prioridade e a
 * ligação com a rede ({@link LinkNetwork}: o nó do AE2 quando ele está instalado; nada sem ele). Não guarda item
 * nenhum: os itens estão na nuvem, então quebrar o bloco não derruba nada.
 *
 * <p>Registra-se no {@code CloudListeners} do serviço para remontar o canal no AE2 quando o dono entra, sai ou
 * muda de situação. O registro é feito no tick, porque o block entity pode carregar antes de o serviço existir
 * (chunks de spawn carregam antes do {@code ServerStartedEvent}).
 */
public class CloudLinkBlockEntity extends BlockEntity implements LinkHost {

    /** Ligação com a rede AE2 ({@code CloudLinkNode}), ou {@link LinkNetwork#NONE} sem o AE2. */
    private final LinkNetwork node;
    private final Runnable onCloudChange = this::refreshMounts;
    private @Nullable UUID owner;
    private String ownerName = "";
    private NetworkAccess access = NetworkAccess.FULL;
    private int priority;
    /**
     * Canal escolhido na tela; null = o padrão do dono (Link novo ou de antes dos canais múltiplos).
     */
    private @Nullable UUID channel;
    private boolean nodeCreated;
    private @Nullable CloudService listeningTo;
    /**
     * Onde o canal já está montado quando este Link não conseguiu montá-lo (para a tela).
     */
    private @Nullable GlobalPos conflict;

    public CloudLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOUD_LINK.get(), pos, state);
        this.node = Ae2Compat.network(this);
    }

    /**
     * Tick do servidor: cria o nó no primeiro tick e se registra no serviço quando ele existir.
     */
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
        updateActive(service);
    }

    /**
     * Aceso ({@link CloudLinkBlock#ACTIVE}) = o Link funciona agora: a nuvem do dono está aberta neste servidor
     * (dono online, com lease) <b>e</b>, se o Link monta o canal numa rede AE2, essa rede está ligada. Sem o AE2, ou
     * no modo "só esta tela", basta a nuvem. Só troca o blockstate quando muda (cada troca vai aos clientes).
     */
    private void updateActive(@Nullable CloudService service) {
        boolean cloudOpen = service != null && owner != null && service.session(owner) != null;
        boolean networkOk = !Ae2Compat.LOADED || !access.mounts() || node.isActive();
        boolean active = cloudOpen && networkOk;
        BlockState state = getBlockState();
        if (state.hasProperty(CloudLinkBlock.ACTIVE) && state.getValue(CloudLinkBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(CloudLinkBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
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

    @Override
    public @Nullable UUID channel() {
        return channel;
    }

    /**
     * Troca o canal deste Link (a tela já conferiu que é do dono); remonta no AE2.
     */
    public void setChannel(@NotNull UUID channel) {
        if (channel.equals(this.channel)) return;
        this.channel = channel;
        setChanged();
        refreshMounts();
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

    /** Ligação com a rede (para a capability do AE2 achar o nó). */
    public @NotNull LinkNetwork network() {
        return node;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        node.save(tag);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putInt("access", access.ordinal());
        tag.putInt("priority", priority);
        if (channel != null) tag.putUUID("channel", channel);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        node.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        access = NetworkAccess.byId(tag.getInt("access"));
        priority = tag.getInt("priority");
        channel = tag.hasUUID("channel") ? tag.getUUID("channel") : null;
    }
}
