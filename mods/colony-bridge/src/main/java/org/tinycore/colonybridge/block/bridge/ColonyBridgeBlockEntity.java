package org.tinycore.colonybridge.block.bridge;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingRequester;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.integration.ae2.BridgeNetwork;
import org.tinycore.colonybridge.logic.terminal.TerminalLink;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.monitor.BridgeContent;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.MonitorLine;
import org.tinycore.colonybridge.block.monitor.MonitorSource;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.logic.bridge.BridgeLogic;
import org.tinycore.colonybridge.logic.bridge.CraftDelivery;
import org.tinycore.colonybridge.logic.crafting.CraftLinks;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;
import org.tinycore.colonybridge.logic.crafting.CraftRules;
import org.tinycore.colonybridge.logic.crafting.CraftableMods;
import org.tinycore.colonybridge.menu.bridge.BridgeSnapshot;
import org.tinycore.colonybridge.registry.ModBlockEntities;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.colonybridge.stats.BridgeStats;
import org.tinycore.colonybridge.stats.StatsSummary;
import org.tinycore.core.block.RedstoneMode;

import java.util.List;
import java.util.Set;

/**
 * Ponte ME → colônia: lê os pedidos em aberto e entrega da rede ME para o armazém.
 * <p>
 * Nó do AE2, dono, permissão, regra do cabo e pausa por redstone vêm de
 * {@link AbstractBridgeBlockEntity}; a lógica de pedidos vive em {@link BridgeLogic}. Aqui ficam só
 * as configurações da tela ({@link BridgeSettings}, {@link CraftSettings}), o filtro, os itens preferidos,
 * as estatísticas e os dados que alimentam a tela ({@link BridgeSnapshot}) e os monitores ({@link MonitorData}).
 */
public class ColonyBridgeBlockEntity extends AbstractBridgeBlockEntity implements MonitorSource {

    /**
     * Requester dos crafts (resultado direto no armazém). Declarado <b>antes</b> do {@code logic}: campos
     * são inicializados na ordem em que aparecem, e o {@code BridgeLogic} já usa este objeto ao ser criado.
     */
    private final CraftLinks craftLinks = new CraftLinks(this::getActionableNode, new CraftDelivery(this),
            this::setChanged);
    private final BridgeLogic logic = new BridgeLogic(this);
    private final ItemFilter filter = new ItemFilter();
    private final PreferredItems preferred = new PreferredItems();
    private final BridgeStats stats = new BridgeStats();
    private BridgeSettings settings = BridgeSettings.DEFAULT;
    private CraftSettings craftSettings = CraftSettings.DEFAULT;
    /** Cache da lista de mods craftáveis para a aba "Mods" (só recalculada com a tela aberta). */
    private List<String> craftableMods = List.of();
    private long craftableModsTime = -1;

    public ColonyBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLONY_BRIDGE.get(), pos, state, ModItems.COLONY_BRIDGE.get(), Config.BRIDGE_IDLE_POWER::get);
        // O AE2 procura o requester pelos serviços do nó ME; precisa ser registrado antes de o nó ser criado.
        managedNode().addService(ICraftingRequester.class, craftLinks);
    }

    // ---------------------------------------------------------------- ciclo

    @Override
    protected void runCycle(ServerLevel level, IGrid grid) {
        // Só uma Ponte por rede ME: com duas, todas param e avisam (o jogador decide qual remover).
        if (!TerminalLink.bridgeAllowed(BridgeNetwork.bridgeCount(grid))) {
            logic.setStatus(BridgeStatus.DUPLICATE_BRIDGE);
            return;
        }
        logic.runCycle(level, grid);
    }

    @Override
    public ColonyBlockType colonyBlockType() {
        return ColonyBlockType.BRIDGE;
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

    public CraftLinks craftLinks() {
        return craftLinks;
    }

    /** Roda um ciclo no próximo tick (ex.: um craft acabou de terminar). */
    public void requestCycle() {
        forceCycleNextTick();
    }

    public PreferredItems getPreferred() {
        return preferred;
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

    /** Aplica preferências de craft já validadas ({@link CraftSettings#sanitized}) e marca para salvar. */
    public void applyCraftSettings(CraftSettings newSettings) {
        craftSettings = newSettings;
        setChanged();
    }

    /**
     * Regras de escolha do craft por tag: o que a ponte configurou, completado com a config do servidor
     * ("padrão do servidor" e lista de preferidos vazia). "Só vanilla" é regra do servidor: a ponte não muda.
     */
    public CraftRules craftRules() {
        CraftPreference preference = craftSettings.preference() != null
                ? craftSettings.preference() : Config.TAG_CRAFT_PREFERENCE.get();
        List<String> ids = preferred.ids();
        if (ids.isEmpty()) {
            ids = List.copyOf(Config.TAG_CRAFT_PREFERRED_ITEMS.get());
        }
        return new CraftRules(preference, ids, craftSettings.modMode(), Set.copyOf(craftSettings.mods()),
                Config.TAG_CRAFT_VANILLA_ONLY.get());
    }

    /** Foto atual do estado para a tela (chamado no servidor, no máximo 1×/s por tela aberta). */
    public BridgeSnapshot snapshot() {
        return new BridgeSnapshot(logic.getStatus(), logic.getColonyName(), settings, craftSettings,
                logic.getReport().counts(), craftableMods());
    }

    /**
     * Mods com item craftável na rede, para a aba "Mods". Percorrer os padrões da rede custa, então o
     * resultado vale por um ciclo ({@code cycleTicks}); sem rede ativa, a lista fica como estava.
     */
    private List<String> craftableMods() {
        if (level == null) {
            return craftableMods;
        }
        long now = level.getGameTime();
        if (craftableModsTime >= 0 && now - craftableModsTime < Config.CYCLE_TICKS.get()) {
            return craftableMods;
        }
        IGridNode node = getActionableNode();
        if (node != null && node.isActive()) {
            craftableMods = CraftableMods.of(node.getGrid().getCraftingService());
            craftableModsTime = now;
        }
        return craftableMods;
    }

    /** Dados para os monitores ligados a esta ponte (chamado no servidor, 1×/s por tela). */
    @Override
    public MonitorData monitorData() {
        return MonitorData.ok(logic.getStatus(), logic.getColonyName(), new BridgeContent(
                logic.getReport().total(), summary(),
                logic.getReport().lines().stream().map(MonitorLine::of).toList()));
    }

    private StatsSummary summary() {
        return level != null ? stats.summary(level.getGameTime()) : StatsSummary.EMPTY;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("settings", settings.save());
        tag.put("craft", craftSettings.save());
        tag.put("filter", filter.save(registries));
        tag.put("preferred", preferred.save(registries));
        tag.put("craftLinks", craftLinks.save(registries));
        tag.put("stats", stats.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        settings = BridgeSettings.load(tag.getCompound("settings"));
        craftSettings = CraftSettings.load(tag.getCompound("craft"));
        filter.load(tag.getCompound("filter"), registries);
        preferred.load(tag.getCompound("preferred"), registries);
        craftLinks.load(CraftLinks.listFrom(tag, "craftLinks"), registries);
        stats.load(tag.getCompound("stats"));
    }
}
