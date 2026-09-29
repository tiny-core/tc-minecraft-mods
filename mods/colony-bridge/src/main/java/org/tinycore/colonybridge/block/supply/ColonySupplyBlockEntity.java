package org.tinycore.colonybridge.block.supply;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.MonitorSource;
import org.tinycore.colonybridge.block.monitor.StockLine;
import org.tinycore.colonybridge.block.monitor.SupplyContent;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.supply.SupplyLogic;
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
 * está em {@link SupplyLogic}, a configuração em {@link StockList} e os números em {@link SupplyStats}.
 * Como {@link MonitorSource}, pode ser mostrado num Monitor da Colônia (cartão de ligação).
 */
public class ColonySupplyBlockEntity extends AbstractBridgeBlockEntity implements MonitorSource {

    private final SupplyLogic logic = new SupplyLogic(this);
    private final StockList stock = new StockList();
    private final SupplyStats stats = new SupplyStats();
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;

    public ColonySupplyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_SUPPLY.get(), pos, state, ModItems.COLONY_SUPPLY.get(), 3.0);
    }

    // ---------------------------------------------------------------- ciclo

    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        logic.runCycle(level, grid);
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

    public StockList getStock() {
        return stock;
    }

    public SupplyStats getStats() {
        return stats;
    }

    /** Muda a quantidade alvo de uma linha (o valor já é limitado dentro da {@link StockList}). */
    public void setAmount(int slot, int amount) {
        stock.setAmount(slot, amount);
        forceCycleNextTick();
        setChanged();
    }

    public void setRedstoneMode(RedstoneMode mode) {
        redstoneMode = mode;
        forceCycleNextTick();
        setChanged();
    }

    /** Foto atual para a tela (chamado no servidor, no máximo 1×/s por tela aberta). */
    public SupplySnapshot snapshot() {
        List<Integer> amounts = new ArrayList<>(StockList.SIZE);
        List<Long> counts = new ArrayList<>(StockList.SIZE);
        for (int slot = 0; slot < StockList.SIZE; slot++) {
            amounts.add(stock.amount(slot));
            counts.add(logic.count(slot));
        }
        return new SupplySnapshot(logic.getStatus(), logic.getColonyName(), redstoneMode,
                List.copyOf(amounts), List.copyOf(counts));
    }

    /** Dados para os monitores ligados a este Abastecedor (chamado no servidor, 1×/s por tela). */
    @Override
    public MonitorData monitorData() {
        List<StockLine> lines = new ArrayList<>();
        for (int slot = 0; slot < StockList.SIZE; slot++) {
            ItemStack item = stock.item(slot);
            if (!item.isEmpty()) {
                lines.add(new StockLine(item.getItem(), StockList.isKeep(slot), stock.amount(slot), logic.count(slot)));
            }
        }
        SupplySummary summary = level != null ? stats.summary(level.getGameTime()) : SupplySummary.EMPTY;
        return MonitorData.ok(logic.getStatus(), logic.getColonyName(), new SupplyContent(summary, List.copyOf(lines)));
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("stock", stock.save(registries));
        tag.putInt("redstone", redstoneMode.ordinal());
        tag.put("stats", stats.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stock.load(tag.getCompound("stock"), registries);
        redstoneMode = RedstoneMode.byId(tag.getInt("redstone"));
        stats.load(tag.getCompound("stats"));
    }
}
