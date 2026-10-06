package org.tinycore.colonybridge.block.encoder;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.integration.ae2.PatternEncoding;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.encoder.EncoderScanner;
import org.tinycore.colonybridge.logic.encoder.EncoderState;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.menu.encoder.EncoderLine;
import org.tinycore.colonybridge.menu.encoder.EncoderSnapshot;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.core.block.RedstoneMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity do TC Pattern Encoder: guarda os slots ({@link EncoderInventory}), confere a colônia a cada ciclo e,
 * com a tela aberta, mantém a lista de pedidos sem padrão ({@link EncoderScanner}).
 * <p>
 * Nó do AE2, dono, permissão e "um por colônia" vêm do {@link AbstractBridgeBlockEntity}. O bloco não precisa da
 * Ponte: só da rede ME (para saber o que ela já crafta e o que tem em estoque) e da colônia.
 * <p>
 * Codificar ({@link #encode}): a receita é conferida de novo na hora e o Blank Pattern só é gasto se o padrão couber
 * na saída. Ele sai do slot do bloco; com o slot vazio, da rede ME. O cliente só diz <b>qual item</b>; a receita e os
 * ingredientes são decididos aqui.
 */
public class PatternEncoderBlockEntity extends AbstractBridgeBlockEntity {

    private final EncoderInventory inventory = new EncoderInventory(this::setChanged);
    private BridgeStatus status = BridgeStatus.STARTING;
    private String colonyName = "";
    private EncoderScanner.Scan scan = new EncoderScanner.Scan(List.of(), 0);
    private long lastScan = Long.MIN_VALUE;

    public PatternEncoderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_ENCODER.get(), pos, state, ModItems.PATTERN_ENCODER.get(),
                Config.ENCODER_IDLE_POWER::get);
    }

    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        BlockPos pos = getBlockPos();
        colonyName = ColonyAccess.colonyNameAt(level, pos);
        if (ColonyAccess.colonyKeyAt(level, pos) == null) {
            status = BridgeStatus.NO_COLONY;
        } else if (!ColonyAccess.canUseAt(level, pos, getOwner())) {
            status = BridgeStatus.NO_PERMISSION;
        } else {
            status = BridgeStatus.IDLE;
        }
    }

    /** true se a rede está ativa e a colônia foi conferida no último ciclo. */
    public boolean isOnline() {
        return status == BridgeStatus.IDLE && managedNode().isActive();
    }

    /**
     * Foto para a tela. Refaz a varredura se a última tem mais de {@code encoderScanTicks} (várias telas abertas
     * dividem a mesma varredura).
     */
    public EncoderSnapshot snapshot(ServerLevel level) {
        refresh(level, false);
        List<EncoderLine> lines = new ArrayList<>();
        scan.entries().forEach(e -> lines.add(e.line()));
        return new EncoderSnapshot(status, colonyName, lines, scan.hidden());
    }

    private void refresh(ServerLevel level, boolean force) {
        long now = level.getGameTime();
        if (!force && now - lastScan < Config.ENCODER_SCAN_TICKS.get()) {
            return;
        }
        lastScan = now;
        IGrid grid = managedNode().getGrid();
        scan = isOnline() && grid != null
                ? EncoderScanner.scan(level, getBlockPos(), grid, inventory.encodedOutputs(level), Config.ENCODER_MAX_LINES.get())
                : new EncoderScanner.Scan(List.of(), 0);
    }

    /**
     * Codifica o item pedido (ou todos os prontos, com {@code item} vazio), até acabar os Blank Patterns ou o espaço.
     *
     * @return quantos padrões foram feitos
     */
    public int encode(ServerLevel level, ItemStack item) {
        if (!isOnline()) {
            return 0;
        }
        refresh(level, true); // nunca codifica a partir de uma lista velha
        IGrid grid = managedNode().getGrid();
        int made = 0;
        for (EncoderScanner.Entry entry : scan.entries()) {
            boolean wanted = item.isEmpty() || ItemStack.isSameItemSameComponents(entry.line().result(), item);
            if (!wanted || entry.line().state() != EncoderState.READY || entry.encodable() == null) {
                continue;
            }
            if (!hasBlank(grid)) {
                break;
            }
            ItemStack pattern = PatternEncoding.encode(level, entry.encodable());
            if (pattern == null || !inventory.fits(pattern)) {
                continue;
            }
            if (takeBlank(grid)) {
                inventory.put(pattern);
                made++;
            }
        }
        if (made > 0) {
            refresh(level, true); // as linhas codificadas passam a "na saída"
        }
        return made;
    }

    /** Há Blank Pattern no slot ou na rede ME (simulação)? */
    private boolean hasBlank(@Nullable IGrid grid) {
        return inventory.hasBlank()
                || grid != null && PatternEncoding.takeBlankFromNetwork(grid, getActionSource(), Actionable.SIMULATE);
    }

    /** Gasta 1 Blank Pattern: primeiro do slot, senão da rede ME. */
    private boolean takeBlank(@Nullable IGrid grid) {
        return inventory.takeBlank()
                || grid != null && PatternEncoding.takeBlankFromNetwork(grid, getActionSource(), Actionable.MODULATE);
    }

    /** Slot de Blank Patterns, para o menu. */
    public ItemStackHandler blanks() {
        return inventory.blanks();
    }

    /** Slots de saída, para o menu. */
    public ItemStackHandler outputs() {
        return inventory.outputs();
    }

    @Override
    public void onBroken(ServerLevel serverLevel) {
        inventory.drop(serverLevel, getBlockPos());
    }

    @Override
    protected void afterCycle(ServerLevel level) {
        syncVisualState(level, AbstractBridgeBlock.STATUS);
    }

    @Override
    public ColonyBlockType colonyBlockType() {
        return ColonyBlockType.PATTERN_ENCODER;
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        inventory.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.load(tag, registries);
    }
}
