package org.tinycore.colonybridge.block.terminal;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.ae2.BridgeNetwork;
import org.tinycore.colonybridge.integration.ae2.GridPower;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.logic.terminal.TerminalLink;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.core.block.RedstoneMode;

import java.util.Objects;

/**
 * Block entity do Terminal do Armazém: o que prende o terminal à rede ME e impede que ele seja "de graça".
 * <p>
 * Nó do AE2 (cabo comum por baixo, um canal, consumo parado da config), dono e permissão vêm do
 * {@link AbstractBridgeBlockEntity}. A cada ciclo confere as restrições ({@link TerminalLink}): a posição
 * está numa colônia onde o dono tem permissão e a rede tem <b>uma</b> Ponte ativa dessa mesma colônia.
 * Só então o terminal fica online ({@link #isOnline}) e a tela libera o armazém.
 * <p>
 * Cada item movido entre armazém e jogador gasta energia da rede ({@link #chargeItems}).
 */
public class WarehouseTerminalBlockEntity extends AbstractBridgeBlockEntity {

    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";

    public WarehouseTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAREHOUSE_TERMINAL.get(), pos, state, ModItems.WAREHOUSE_TERMINAL.get(),
                Config.TERMINAL_IDLE_POWER::get);
    }

    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        BlockPos pos = getBlockPos();
        String colony = ColonyAccess.colonyKeyAt(level, pos);
        colonyName = ColonyAccess.colonyNameAt(level, pos);
        if (colony == null) {
            status = BridgeStatus.NO_COLONY;
            return;
        }
        if (!ColonyAccess.canUseAt(level, pos, getOwner())) {
            status = BridgeStatus.NO_PERMISSION;
            return;
        }
        ColonyBridgeBlockEntity bridge = BridgeNetwork.singleBridge(grid);
        boolean active = bridge != null && TerminalLink.isActive(bridge.getStatus());
        boolean sameColony = bridge != null
                && Objects.equals(colony, ColonyAccess.colonyKeyAt(level, bridge.getBlockPos()));
        status = TerminalLink.terminalStatus(BridgeNetwork.bridgeCount(grid), active, sameColony);
    }

    /** true se a rede está ativa (energia + canal) e as restrições do último ciclo foram cumpridas. */
    public boolean isOnline() {
        return status == BridgeStatus.IDLE && managedNode().isActive();
    }

    /**
     * Cobra da rede a energia de {@code items} itens movidos ({@code terminalEnergyPerItem} cada), com o
     * multiplicador de energia da config do AE2 ({@link GridPower#chargeAe}). Se a rede não tiver o suficiente,
     * tira o que houver (a ação já só acontece com a rede ativa, que exige energia).
     */
    public void chargeItems(long items) {
        IGrid grid = managedNode().getGrid();
        if (grid == null || items <= 0) {
            return;
        }
        GridPower.chargeAe(grid, items * Config.TERMINAL_ENERGY_PER_ITEM.get());
    }

    @Override
    protected void afterCycle(ServerLevel level) {
        syncVisualState(level, AbstractBridgeBlock.STATUS);
    }

    @Override
    public ColonyBlockType colonyBlockType() {
        return ColonyBlockType.TERMINAL;
    }

    @Override
    protected RedstoneMode redstoneMode() {
        return RedstoneMode.IGNORED;
    }

    @Override
    public BridgeStatus getStatus() {
        return status;
    }

    @Override
    protected void setStatus(BridgeStatus status) {
        this.status = status;
    }

    public String getColonyName() {
        return colonyName;
    }
}
