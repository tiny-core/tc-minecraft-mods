package org.tinycore.colonybridge.block;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
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
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.ae2.CableRules;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.core.block.RedstoneMode;

import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/**
 * Base dos blocos que ligam uma rede ME a uma colônia (Ponte — {@code ColonyBridgeBlockEntity} — e o bloco de
 * abastecimento). Concentra o que os dois têm em comum, para nenhum deles virar um arquivo gigante:
 * <ul>
 *   <li>nó da grid do AE2 e seu ciclo de vida;</li>
 *   <li>dono do bloco e checagem de permissão na colônia;</li>
 *   <li>regra de conexão do cabo ({@link CableRules}: só por baixo, só cabo comum);</li>
 *   <li>contagem de ticks, pausa por redstone e o esqueleto do ciclo;</li>
 *   <li>vaga "um de cada tipo por colônia" ({@link ColonySlots}): ocupada a cada ciclo e solta quando o
 *       bloco é removido ({@link #releaseColonySlot}, chamado pelo {@code onRemove} do bloco).</li>
 * </ul>
 * O que cada bloco faz de fato entra em {@link #runCycle}; o estado fica na subclasse
 * ({@link #getStatus}/{@link #setStatus}), porque cada uma guarda o seu de um jeito.
 */
public abstract class AbstractBridgeBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost {

    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    /** Consumo parado (AE/t), lido da config só no servidor, ao carregar ({@link #onLoad}). */
    private final DoubleSupplier idlePowerUsage;
    /** Jogador que colocou o bloco; a permissão dele na colônia é conferida a cada ciclo. */
    private @Nullable UUID owner;
    private int tickCounter;
    /** Se o lado de baixo está exposto agora (cabo válido encontrado). */
    private boolean cableAllowed;
    /** Um vizinho mudou: reavaliar o cabo no próximo tick (fora do evento de vizinhança do AE2). */
    private boolean cableCheckPending = true;
    /** Colônia cuja vaga este bloco ocupa (salva no NBT para soltar a vaga mesmo se a colônia sumir). */
    private @Nullable String claimedColony;

    protected AbstractBridgeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                        ItemLike visual, DoubleSupplier idlePowerUsage) {
        super(type, pos, state);
        this.idlePowerUsage = idlePowerUsage;
        this.mainNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.noneOf(Direction.class)) // aberto só com cabo válido
                .setTagName("node")
                .setVisualRepresentation(visual);
        this.actionSource = IActionSource.ofMachine(this);
    }

    /**
     * Nó ME gerenciado, para a subclasse registrar serviços no construtor (ex.: o requester de crafts).
     * O AE2 só lê os serviços quando o nó é criado ({@link #onLoad}), então registrar depois não vale.
     */
    protected IManagedGridNode managedNode() {
        return mainNode;
    }

    // ---------------------------------------------------------------- o que cada bloco define

    /** Trabalho do bloco, com a rede ME ativa e sem pausa por redstone. */
    protected abstract void runCycle(ServerLevel level, IGrid grid);

    /** Quando o bloco funciona em relação à redstone (vem das configurações da subclasse). */
    protected abstract RedstoneMode redstoneMode();

    public abstract BridgeStatus getStatus();

    /** Tipo do bloco na regra "um de cada tipo por colônia". */
    public abstract ColonyBlockType colonyBlockType();

    /** Registra o estado do ciclo (a subclasse decide onde guardar). */
    protected abstract void setStatus(BridgeStatus status);

    /** Gancho no fim de cada ciclo (ex.: atualizar o visual do bloco, marcar para salvar). */
    protected void afterCycle(ServerLevel level) {
    }

    // ---------------------------------------------------------------- ciclo de vida do nó

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            // Config do servidor: lida aqui (e não no construtor), porque o block entity também é criado no
            // cliente, onde ela pode ainda não existir. {@code DoubleSupplier} ≈ {@code Func<double>} em C#.
            mainNode.setIdlePowerUsage(idlePowerUsage.getAsDouble());
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

    // ---------------------------------------------------------------- tick

    /**
     * Um passo do bloco. O trabalho pesado só acontece a cada {@code cycleTicks}; nos demais ticks
     * só há a reavaliação pendente do cabo, que é barata.
     */
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
        if (!holdsColonySlot(serverLevel)) {
            setStatus(BridgeStatus.DUPLICATE_IN_COLONY);
        } else if (!cableAllowed) {
            setStatus(BridgeStatus.INVALID_CABLE);
        } else if (!redstoneMode().allows(serverLevel.hasNeighborSignal(worldPosition))) {
            setStatus(BridgeStatus.PAUSED);
        } else if (mainNode.isActive()) {
            runCycle(serverLevel, mainNode.getGrid());
        } else {
            setStatus(BridgeStatus.OFFLINE);
        }
        afterCycle(serverLevel);
    }

    // ---------------------------------------------------------------- um por colônia

    /**
     * Ocupa (ou confirma) a vaga da colônia onde o bloco está. Fora de colônia não há vaga: true, e o ciclo
     * segue para dar {@code NO_COLONY}. Se a borda da colônia mudou e o bloco passou para outra, a vaga da
     * antiga é solta. Roda antes da checagem da rede para um bloco sem energia não perder a vaga.
     * Também chamado ao colocar o bloco, para dois blocos colocados no mesmo instante não passarem juntos.
     */
    boolean holdsColonySlot(ServerLevel serverLevel) {
        String colony = ColonyAccess.colonyKeyAt(serverLevel, worldPosition);
        if (claimedColony != null && !claimedColony.equals(colony)) {
            ColonySlots.release(serverLevel, claimedColony, colonyBlockType(), worldPosition);
            setClaimedColony(null);
        }
        if (colony == null) {
            return true;
        }
        if (!ColonySlots.claim(serverLevel, colony, colonyBlockType(), worldPosition)) {
            return false;
        }
        setClaimedColony(colony);
        return true;
    }

    /**
     * O bloco foi removido do mundo (quebrado, explodido, trocado por comando): solta a vaga da colônia.
     * Chamado pelo {@code onRemove} do bloco, que não roda quando o chunk só descarrega; por isso a vaga
     * não é solta em {@link #setRemoved}.
     */
    public void releaseColonySlot(ServerLevel serverLevel) {
        if (claimedColony != null) {
            ColonySlots.release(serverLevel, claimedColony, colonyBlockType(), worldPosition);
            claimedColony = null;
        }
    }

    /** O bloco saiu do mundo: derruba o que ele guarda de itens de verdade (ex.: tablet no carregador da Ponte). */
    public void dropContents(ServerLevel serverLevel) {
    }

    private void setClaimedColony(@Nullable String colony) {
        if (!Objects.equals(claimedColony, colony)) {
            claimedColony = colony;
            setChanged();
        }
    }

    /** Faz o próximo tick já rodar um ciclo (ex.: o jogador mudou uma configuração na tela). */
    protected void forceCycleNextTick() {
        tickCounter = Config.CYCLE_TICKS.get();
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
    protected void syncVisualState(ServerLevel serverLevel, EnumProperty<BridgeVisualState> property) {
        BlockState state = getBlockState();
        BridgeVisualState visual = BridgeVisualState.of(getStatus());
        if (state.hasProperty(property) && state.getValue(property) != visual) {
            serverLevel.setBlock(worldPosition, state.setValue(property, visual), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- dono e permissão

    /** Registra o dono para o AE2 (segurança da rede) e para a checagem de permissão da colônia. */
    public void setOwner(Player player) {
        mainNode.setOwningPlayer(player);
        owner = player.getUUID();
        setChanged();
    }

    public @Nullable UUID getOwner() {
        return owner;
    }

    /**
     * Quem pode abrir a tela e mudar configurações: quem tem permissão na colônia; fora de colônia,
     * só o dono (ou qualquer um, se o bloco é antigo e não tem dono salvo).
     */
    public boolean canConfigure(Player player) {
        return level != null && ColonyAccess.canConfigureBridge(level, worldPosition, player.getUUID(), owner);
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
        if (claimedColony != null) {
            tag.putString("colonySlot", claimedColony);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        claimedColony = tag.contains("colonySlot") ? tag.getString("colonySlot") : null;
    }

    private enum NodeListener implements IGridNodeListener<AbstractBridgeBlockEntity> {
        INSTANCE;

        @Override
        public void onSaveChanges(AbstractBridgeBlockEntity host, IGridNode node) {
            host.setChanged();
        }
    }
}
