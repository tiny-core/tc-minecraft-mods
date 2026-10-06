package org.tinycore.colonybridge.block.supply;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.MonitorSource;
import org.tinycore.colonybridge.block.monitor.StockLine;
import org.tinycore.colonybridge.block.monitor.SupplyContent;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.logic.supply.SupplyLineResults;
import org.tinycore.colonybridge.logic.supply.SupplyLogic;
import org.tinycore.colonybridge.logic.target.TargetLine;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.logic.target.TargetListHost;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.supply.SupplyLineStat;
import org.tinycore.colonybridge.menu.supply.SupplySnapshot;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.colonybridge.stats.SupplyStats;
import org.tinycore.colonybridge.stats.SupplySummary;
import org.tinycore.core.block.RedstoneMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Bloco de abastecimento: mantém itens escolhidos no armazém da colônia e devolve o excedente à rede ME.
 * É o caminho contrário da ponte, que só entrega o que os cidadãos pedem.
 * <p>
 * Nó do AE2, dono, permissão, cabo e redstone vêm de {@link AbstractBridgeBlockEntity}; o trabalho em si
 * está em {@link SupplyLogic}, a configuração nas duas listas de linhas ({@link TargetList}: Manter e
 * Excedente, editadas pela tela via {@link TargetListHost}) e os números em {@link SupplyStats}.
 * Como {@link MonitorSource}, pode ser mostrado num Monitor da Colônia (cartão de ligação).
 * <p>
 * Blocos salvos antes das listas (grade 18 + 18, {@link StockList}) são convertidos ao carregar.
 */
public class ColonySupplyBlockEntity extends AbstractBridgeBlockEntity implements MonitorSource, TargetListHost {

    private final SupplyLogic logic = new SupplyLogic(this);
    private final TargetList keepList = new TargetList(TargetListKind.KEEP);
    private final TargetList surplusList = new TargetList(TargetListKind.SURPLUS);
    private final SupplyStats stats = new SupplyStats();
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    /** Pedir ao AE2 o craft do que falta nas linhas "manter" (botão na tela; padrão ligado). */
    private boolean craftMissing = true;

    public ColonySupplyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_SUPPLY.get(), pos, state, ModItems.COLONY_SUPPLY.get(), Config.SUPPLY_IDLE_POWER::get);
    }

    // ---------------------------------------------------------------- ciclo

    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        int maxLines = Config.LIST_MAX_LINES.get();
        if (keepList.trim(maxLines) | surplusList.trim(maxLines)) { // | (não ||): corta as duas
            setChanged(); // o admin baixou o limite na config
        }
        logic.runCycle(level, grid);
    }

    @Override
    public ColonyBlockType colonyBlockType() {
        return ColonyBlockType.SUPPLY;
    }

    @Override
    protected RedstoneMode redstoneMode() {
        return redstoneMode;
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
            setChanged(); // só marca para salvar quando o ciclo moveu itens
        }
    }

    // ---------------------------------------------------------------- tela

    public TargetList getKeepList() {
        return keepList;
    }

    public TargetList getSurplusList() {
        return surplusList;
    }

    @Override
    public @Nullable TargetList targetList(TargetListKind kind) {
        return switch (kind) {
            case KEEP -> keepList;
            case SURPLUS -> surplusList;
            case FILTER -> null;
        };
    }

    @Override
    public void onTargetListChanged(TargetListKind kind) {
        forceCycleNextTick();
        setChanged();
    }

    public SupplyStats getStats() {
        return stats;
    }

    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        forceCycleNextTick();
        setChanged();
    }

    public boolean craftsMissing() {
        return craftMissing;
    }

    public void setCraftMissing(boolean value) {
        craftMissing = value;
        forceCycleNextTick();
        setChanged();
    }

    /** Foto atual para a tela (chamado no servidor, no máximo 1×/s por tela aberta). */
    public SupplySnapshot snapshot() {
        return new SupplySnapshot(logic.getStatus(), logic.getColonyName(), redstoneMode, craftMissing,
                lineStats(keepList, logic.results(true)), lineStats(surplusList, logic.results(false)));
    }

    private static List<SupplyLineStat> lineStats(TargetList list, SupplyLineResults results) {
        List<SupplyLineStat> stats = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            stats.add(new SupplyLineStat(results.warehouse(i), results.status(i)));
        }
        return List.copyOf(stats);
    }

    /** Dados para os monitores ligados a este Abastecedor (chamado no servidor, 1×/s por tela). */
    @Override
    public MonitorData monitorData() {
        List<StockLine> lines = new ArrayList<>();
        addStockLines(lines, keepList, logic.results(true), true);
        addStockLines(lines, surplusList, logic.results(false), false);
        long now = level != null ? level.getGameTime() : 0;
        SupplySummary summary = level != null ? stats.summary(now) : SupplySummary.EMPTY;
        // Em minutos (1200 ticks) para o valor mudar pouco: o monitor só reenvia quando algo muda.
        long minutesSinceMove = logic.lastMoveTime() < 0 ? -1 : (now - logic.lastMoveTime()) / 1200;
        return MonitorData.ok(logic.getStatus(), logic.getColonyName(),
                new SupplyContent(summary, List.copyOf(lines), minutesSinceMove));
    }

    private static void addStockLines(List<StockLine> out, TargetList list, SupplyLineResults results, boolean keep) {
        for (int i = 0; i < list.size(); i++) {
            TargetLine line = list.lines().get(i);
            out.add(StockLine.of(line, keep, results.warehouse(i), results.network(i), results.status(i)));
        }
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("keepList", keepList.save(registries));
        tag.put("surplusList", surplusList.save(registries));
        tag.putInt("redstone", redstoneMode.ordinal());
        tag.putBoolean("craftMissing", craftMissing);
        tag.put("stats", stats.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("keepList")) {
            keepList.load(tag.getCompound("keepList"), registries);
            surplusList.load(tag.getCompound("surplusList"), registries);
        } else {
            // Bloco salvo no formato de grade (antes das listas): converte uma vez; o próximo save já é novo.
            keepList.clear();
            surplusList.clear();
            StockList legacy = new StockList();
            legacy.load(tag.getCompound("stock"), registries);
            legacy.exportTo(keepList, surplusList);
        }
        redstoneMode = RedstoneMode.byId(tag.getInt("redstone"));
        craftMissing = !tag.contains("craftMissing") || tag.getBoolean("craftMissing"); // blocos antigos: ligado
        stats.load(tag.getCompound("stats"));
    }
}
