package org.tinycore.colonybridge.menu.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.block.bridge.PreferredItems;
import org.tinycore.colonybridge.logic.target.TargetListHost;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.TargetListEditor;
import org.tinycore.colonybridge.menu.TargetListMenu;
import org.tinycore.colonybridge.menu.TargetListSync;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.menu.tablet.TabletMenu;
import org.tinycore.colonybridge.menu.tablet.TabletView;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.colonybridge.registry.ModMenus;
import org.tinycore.core.menu.AbstractGhostMenu;

/**
 * "Container" da tela da ponte. No Minecraft toda tela ligada a um bloco tem duas metades:
 * o menu (existe no servidor <b>e</b> no cliente) e a {@code Screen} (só no cliente, em {@code client/}).
 * <p>
 * Slots: 0..17 são os ghost slots dos itens preferidos ({@link AbstractGhostMenu}), 18 é o carregador do tablet
 * ({@link ChargerSlot}, slot de verdade, só na aba "Geral") e depois o inventário do jogador (só para pegar itens e clicar nos ghost slots / ícones da lista). O <b>filtro</b> não é slot: é uma
 * lista de linhas ({@link TargetListMenu}) editada por pacote. No cliente os preferidos só aparecem na aba
 * deles ({@link BridgeTab}); o inventário, nas abas "Filtro" e "Preferidos".
 * <p>
 * O menu do servidor envia um {@link BridgeSnapshot} quando os dados mudam (checagem a cada
 * {@link #SNAPSHOT_INTERVAL_TICKS}); o do cliente guarda o último recebido para a tela desenhar.
 */
public class ColonyBridgeMenu extends AbstractGhostMenu implements TargetListMenu, TabletMenu {

    /** Frequência máxima de envio do snapshot: 1×/s. */
    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    /** Posições (relativas à tela) usadas também pela {@code ColonyBridgeScreen} para desenhar o fundo. */
    public static final int FILTER_X = 20; // 9 colunas centralizadas na janela de 202 px
    public static final int FILTER_Y = 64;
    public static final int INVENTORY_Y = 181;
    public static final int HOTBAR_Y = 239;

    public static final int GHOST_COUNT = PreferredItems.SIZE;
    /** Carregador do tablet: índice no menu e posição (aba "Geral"). */
    public static final int CHARGER_INDEX = GHOST_COUNT;
    public static final int CHARGER_X = 9;
    public static final int CHARGER_Y = 131;

    private final BlockPos pos;
    /** Só no servidor: o block entity. */
    private final @Nullable ColonyBridgeBlockEntity bridge;
    /** Por onde a tela foi aberta (bloco ou tablet): decide quando ela continua válida. */
    private final MenuAccess access;
    private final @Nullable TabletView tabletView;
    private final Player player;

    private @Nullable BridgeSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido. */
    private BridgeSnapshot snapshot = BridgeSnapshot.EMPTY;
    /** Aba aberta: no cliente vem da tela; no servidor, do {@code BridgeTabPayload}. */
    private BridgeTab tab = BridgeTab.GENERAL;
    private final TargetListSync lists = new TargetListSync(TargetListKind.FILTER);

    /** Construtor do servidor, chamado ao abrir a tela. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, ColonyBridgeBlockEntity bridge, MenuAccess access) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId,
                bridge.getPreferred().items(), bridge::setChanged);
        this.pos = bridge.getBlockPos();
        this.bridge = bridge;
        this.access = access;
        this.tabletView = access.tabletView();
        this.player = inventory.player;
        addSlots(inventory, bridge.getCharger().handler());
    }

    /** Construtor do cliente: o servidor manda só a posição do bloco nos "dados extras" da abertura. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId,
                NonNullList.withSize(GHOST_COUNT, ItemStack.EMPTY), () -> {});
        this.pos = extraData.readBlockPos();
        this.bridge = null;
        this.access = MenuAccess.CLIENT;
        this.tabletView = TabletView.read(extraData);
        this.player = inventory.player;
        addSlots(inventory, new ItemStackHandler(1)); // cópia vazia: o conteúdo vem sincronizado do servidor
    }

    private void addSlots(Inventory inventory, IItemHandler charger) {
        for (int i = 0; i < PreferredItems.SIZE; i++) {
            addGhostSlot(i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18, () -> isVisible(BridgeTab.PREFERRED));
        }
        addSlot(new ChargerSlot(charger, CHARGER_X, CHARGER_Y, () -> isVisible(BridgeTab.GENERAL)));
        addPlayerInventory(inventory, FILTER_X, INVENTORY_Y, HOTBAR_Y,
                () -> bridge != null || tab.showsInventory());
    }

    /** No servidor não existe "aba visível": os slots ficam sempre ativos; no cliente seguem a aba aberta. */
    private boolean isVisible(BridgeTab slotTab) {
        return bridge != null || tab == slotTab;
    }

    /** Shift-clique no inventário só vale na aba "Preferidos" (na aba "Filtro" vira linha nova, abaixo). */
    @Override
    protected int quickMoveEnd() {
        return tab == BridgeTab.PREFERRED ? GHOST_COUNT : 0;
    }

    /**
     * Shift-clique:
     * <ul>
     *   <li>no carregador → volta o tablet para o inventário;</li>
     *   <li>num tablet do inventário, na aba "Geral" → vai para o carregador;</li>
     *   <li>na aba "Filtro" → vira uma linha nova do filtro (nunca move o item);</li>
     *   <li>na aba "Preferidos" → copia para o primeiro ghost slot livre ({@link AbstractGhostMenu}).</li>
     * </ul>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (index == CHARGER_INDEX) {
            moveItemStackTo(stack, CHARGER_INDEX + 1, slots.size(), true);
            slots.get(index).setChanged();
            return ItemStack.EMPTY;
        }
        if (tab == BridgeTab.GENERAL && index > CHARGER_INDEX && stack.is(ModItems.COLONY_TABLET.get())) {
            moveItemStackTo(stack, CHARGER_INDEX, CHARGER_INDEX + 1, false);
            slots.get(index).setChanged();
            return ItemStack.EMPTY;
        }
        if (tab != BridgeTab.FILTER || bridge == null) {
            return super.quickMoveStack(player, index);
        }
        if (index > CHARGER_INDEX && canEditGhosts(player)
                && TargetListEditor.addStack(bridge.getFilter(), slots.get(index).getItem())) {
            bridge.onTargetListChanged(TargetListKind.FILTER);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canEditGhosts(Player player) {
        return bridge == null || bridge.canConfigure(player);
    }

    // ---------------------------------------------------------------- sincronização

    /**
     * Chamado pelo servidor todo tick enquanto a tela está aberta. Monta o snapshot a cada
     * segundo e só envia se algo mudou. Os slots são sincronizados pelo próprio {@code super}.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (bridge == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        lists.sendChanged(serverPlayer, containerId, bridge);
        if (--ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SNAPSHOT_INTERVAL_TICKS;
        BridgeSnapshot current = bridge.snapshot();
        if (current.equals(lastSent)) {
            return;
        }
        lastSent = current;
        PacketDistributor.sendToPlayer(serverPlayer, new BridgeSnapshotPayload(containerId, current));
    }

    /** Força o envio no próximo tick (ex.: logo após o jogador mudar uma configuração). */
    public void requestSync() {
        ticksUntilSync = 0;
    }

    @Override
    public TargetListSync targetLists() {
        return lists;
    }

    @Override
    public @Nullable TargetListHost listHost() {
        return bridge;
    }

    /** A Ponte só tem a lista do filtro: nada a escolher. */
    @Override
    public void setActiveList(TargetListKind kind) {
    }

    /** Só no servidor: a ponte desta tela (null no cliente). */
    public @Nullable ColonyBridgeBlockEntity getBridge() {
        return bridge;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BridgeSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(BridgeSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public BridgeTab getTab() {
        return tab;
    }

    /** A tela (cliente) ou o {@code BridgeTabPayload} (servidor) avisam qual aba está aberta. */
    public void setTab(BridgeTab tab) {
        this.tab = tab;
    }

    /** Fecha a tela quando o acesso deixa de valer (bloco sumiu, jogador longe, tablet sem bateria...). */
    @Override
    public boolean stillValid(Player player) {
        return access.stillValid(player);
    }

    @Override
    public @Nullable TabletView tabletView() {
        return tabletView;
    }

}
