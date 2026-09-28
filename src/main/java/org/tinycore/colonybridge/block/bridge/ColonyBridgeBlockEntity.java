package org.tinycore.colonybridge.block.bridge;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.RedstoneMode;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.MonitorLine;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.bridge.BridgeLogic;
import org.tinycore.colonybridge.menu.bridge.BridgeSnapshot;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.colonybridge.stats.BridgeStats;
import org.tinycore.colonybridge.stats.StatsSummary;

/**
 * Ponte ME → colônia: lê os pedidos em aberto e entrega da rede ME para o armazém.
 * <p>
 * Nó do AE2, dono, permissão, regra do cabo e pausa por redstone vêm de
 * {@link AbstractBridgeBlockEntity}; a lógica de pedidos vive em {@link BridgeLogic}. Aqui ficam só
 * as configurações da tela ({@link BridgeSettings}), o filtro, as estatísticas e os dados que
 * alimentam a tela ({@link BridgeSnapshot}) e os monitores ({@link MonitorData}).
 */
public class ColonyBridgeBlockEntity extends AbstractBridgeBlockEntity {

    private final BridgeLogic logic = new BridgeLogic(this);
    private final ItemFilter filter = new ItemFilter();
    private final BridgeStats stats = new BridgeStats();
    private BridgeSettings settings = BridgeSettings.DEFAULT;

    public ColonyBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_BRIDGE.get(), pos, state, ModItems.COLONY_BRIDGE.get(), 4.0);
    }

    // ---------------------------------------------------------------- ciclo

    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        logic.runCycle(level, grid);
    }

    @Override
    protected RedstoneMode redstoneMode() {
        return settings.redstoneMode();
    }

    @Override
    public BridgeStatus getStatus() {
        return logic.getStatus();
    }

    @Override
    protected void setStatus(BridgeStatus status) {
        logic.setStatus(status);
    }

    @Override
    protected void afterCycle(ServerLevel level) {
        syncVisualState(level, AbstractBridgeBlock.STATUS);
        if (stats.consumeDirty()) {
            setChanged(); // só marca para salvar quando houve entrega/craft no ciclo
        }
    }

    // ---------------------------------------------------------------- tela

    public BridgeSettings getSettings() {
        return settings;
    }

    public BridgeStats getStats() {
        return stats;
    }

    public ItemFilter getFilter() {
        return filter;
    }

    /** true se o filtro deixa este item sair da rede (entrega ou craft). */
    public boolean filterAllows(ItemStack stack) {
        return settings.filterMode().allows(filter.contains(stack, settings.exactMatch()));
    }

    /** Aplica configurações já validadas (ver {@code ModNetwork}) e marca o bloco para salvar. */
    public void applySettings(BridgeSettings newSettings) {
        settings = newSettings;
        forceCycleNextTick(); // a tela reflete a mudança já no próximo ciclo
        setChanged();
    }

    /** Foto atual do estado para a tela (chamado no servidor, no máximo 1×/s por tela aberta). */
    public BridgeSnapshot snapshot() {
        return new BridgeSnapshot(logic.getStatus(), logic.getColonyName(), settings,
                logic.getReport().lines(), logic.getReport().total(), summary());
    }

    /** Dados para os monitores ligados a esta ponte (chamado no servidor, 1×/s por tela). */
    public MonitorData monitorData() {
        return new MonitorData(MonitorData.LinkState.OK, logic.getStatus(), logic.getColonyName(),
                logic.getReport().total(), summary(),
                logic.getReport().lines().stream().map(MonitorLine::of).toList());
    }

    private StatsSummary summary() {
        return level != null ? stats.summary(level.getGameTime()) : StatsSummary.EMPTY;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("settings", settings.save());
        tag.put("filter", filter.save(registries));
        tag.put("stats", stats.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = BridgeSettings.load(tag.getCompound("settings"));
        filter.load(tag.getCompound("filter"), registries);
        stats.load(tag.getCompound("stats"));
    }
}
