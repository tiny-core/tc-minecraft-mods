package org.tinycore.colonybridge.block;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.ae2.CableRules;
import org.tinycore.colonybridge.logic.BridgeLogic;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.menu.BridgeSnapshot;
import org.tinycore.colonybridge.registry.ModRegistries;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Ponto de ligação entre uma rede ME e a colônia onde o bloco está colocado.
 * Só expõe o nó da grid e guarda o dono; a lógica de pedidos vive em {@link BridgeLogic}.
 * <p>
 * Conexão restrita ({@link CableRules}): o nó só fica exposto no lado de baixo, e só enquanto houver
 * um cabo comum ali. Sem cabo válido nenhum lado é exposto, então o AE2 nem desenha a conexão.
 * <p>
 * Também guarda as configurações da tela ({@link BridgeSettings}) e monta o {@link BridgeSnapshot}
 * que o menu envia ao cliente.
 */
public class ColonyBridgeBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost {

    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private final BridgeLogic logic;
    /** Jogador que colocou a ponte; a permissão dele na colônia é conferida a cada ciclo. */
    private @Nullable UUID owner;
    private BridgeSettings settings = BridgeSettings.DEFAULT;
    private int tickCounter;
    /** Se o lado de baixo está exposto agora (cabo válido encontrado). */
    private boolean cableAllowed;
    /** Um vizinho mudou: reavaliar o cabo no próximo tick (fora do evento de vizinhança do AE2). */
    private boolean cableCheckPending = true;

    public ColonyBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.COLONY_BRIDGE_BE.get(), pos, state);
        this.mainNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(4.0)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.noneOf(Direction.class)) // aberto só com cabo válido
                .setTagName("node")
                .setVisualRepresentation(ModRegistries.COLONY_BRIDGE_ITEM.get());
        this.actionSource = IActionSource.ofMachine(this);
        this.logic = new BridgeLogic(this);
    }

    // ---------------------------------------------------------------- ciclo de vida do nó

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, be -> {
                be.refreshCableConnection(); // define os lados antes de criar, sem conectar e desconectar
                be.mainNode.create(be.level, be.worldPosition);
            });
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    /** Registra o dono para o AE2 (segurança da rede) e para a checagem de permissão da colônia. */
    public void setOwner(Player player) {
        mainNode.setOwningPlayer(player);
        owner = player.getUUID();
        setChanged();
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    // ---------------------------------------------------------------- tick

    public void serverTick() {
        if (cableCheckPending) {
            cableCheckPending = false;
            refreshCableConnection();
        }
        if (++tickCounter < Config.CYCLE_TICKS.get()) {
            return;
        }
        tickCounter = 0;

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        refreshCableConnection(); // rede de segurança caso algum evento de vizinhança tenha escapado
        if (!cableAllowed) {
            logic.setStatus(BridgeStatus.INVALID_CABLE);
        } else if (!settings.redstoneMode().allows(serverLevel.hasNeighborSignal(worldPosition))) {
            logic.setStatus(BridgeStatus.PAUSED);
        } else if (mainNode.isActive()) {
            logic.runCycle(serverLevel, mainNode.getGrid());
        } else {
            logic.setStatus(BridgeStatus.OFFLINE);
        }
        syncVisualState(serverLevel);
    }

    // ---------------------------------------------------------------- tela

    public BridgeSettings getSettings() {
        return settings;
    }

    /** Aplica configurações já validadas (ver {@code ModNetwork}) e marca o bloco para salvar. */
    public void applySettings(BridgeSettings newSettings) {
        settings = newSettings;
        tickCounter = Config.CYCLE_TICKS.get(); // roda um ciclo no próximo tick para a tela refletir logo
        setChanged();
    }

    /**
     * Quem pode abrir a tela e mudar configurações: quem tem permissão na colônia; fora de colônia,
     * só o dono (ou qualquer um, se a ponte é antiga e não tem dono salvo).
     */
    public boolean canConfigure(Player player) {
        return level != null && ColonyAccess.canConfigureBridge(level, worldPosition, player.getUUID(), owner);
    }

    /** Foto atual do estado para a tela (chamado no servidor, no máximo 1×/s por tela aberta). */
    public BridgeSnapshot snapshot() {
        return new BridgeSnapshot(logic.getStatus(), logic.getColonyName(), settings.craftingEnabled(),
                settings.redstoneMode(), logic.getReport().lines(), logic.getReport().total());
    }

    /** Chamado pelo bloco quando um vizinho muda; a checagem acontece no próximo tick. */
    public void onNeighborChanged() {
        cableCheckPending = true;
    }

    /** Expõe o lado de baixo só se houver cabo válido; mexe no nó apenas quando o resultado muda. */
    private void refreshCableConnection() {
        boolean allowed = level != null && CableRules.hasAllowedCable(level, worldPosition);
        if (allowed == cableAllowed) {
            return;
        }
        cableAllowed = allowed;
        mainNode.setExposedOnSides(allowed
                ? EnumSet.of(CableRules.CONNECTION_SIDE)
                : EnumSet.noneOf(Direction.class));
    }

    /**
     * Copia o status para o blockstate, só quando o visual muda (cada troca envia um pacote aos clientes).
     * Trocar o estado do mesmo bloco mantém este block entity; {@code UPDATE_CLIENTS} evita avisar vizinhos.
     */
    private void syncVisualState(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        BridgeVisualState visual = BridgeVisualState.of(logic.getStatus());
        if (state.hasProperty(ColonyBridgeBlock.STATUS) && state.getValue(ColonyBridgeBlock.STATUS) != visual) {
            serverLevel.setBlock(worldPosition, state.setValue(ColonyBridgeBlock.STATUS, visual), Block.UPDATE_CLIENTS);
        }
    }

    public BridgeStatus getStatus() {
        return logic.getStatus();
    }

    public IActionSource getActionSource() {
        return actionSource;
    }

    // ---------------------------------------------------------------- AE2

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
        tag.put("settings", settings.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        settings = BridgeSettings.load(tag.getCompound("settings"));
    }

    private enum NodeListener implements IGridNodeListener<ColonyBridgeBlockEntity> {
        INSTANCE;

        @Override
        public void onSaveChanges(ColonyBridgeBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    }
}
