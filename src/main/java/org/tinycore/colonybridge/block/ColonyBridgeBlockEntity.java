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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.logic.BridgeLogic;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.registry.ModRegistries;

import java.util.EnumSet;

/**
 * Ponto de ligação entre uma rede ME e a colónia onde o bloco está colocado.
 * Só expõe o nó da grid; a lógica de pedidos vive em {@link BridgeLogic}.
 */
public class ColonyBridgeBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost {

    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;
    private final BridgeLogic logic;
    private int tickCounter;

    public ColonyBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.COLONY_BRIDGE_BE.get(), pos, state);
        this.mainNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(4.0)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
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
            GridHelper.onFirstTick(this, be -> be.mainNode.create(be.level, be.worldPosition));
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

    public void setOwner(Player player) {
        mainNode.setOwningPlayer(player);
    }

    // ---------------------------------------------------------------- tick

    public void serverTick() {
        if (++tickCounter < Config.CYCLE_TICKS.get()) {
            return;
        }
        tickCounter = 0;

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!mainNode.isActive()) {
            logic.setStatus(BridgeStatus.OFFLINE);
            return;
        }
        logic.runCycle(serverLevel, mainNode.getGrid());
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
    }

    private enum NodeListener implements IGridNodeListener<ColonyBridgeBlockEntity> {
        INSTANCE;

        @Override
        public void onSaveChanges(ColonyBridgeBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }
    }
}
