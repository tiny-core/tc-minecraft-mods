package org.tinycore.colonybridge.menu.bridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.block.bridge.ItemFilter;
import org.tinycore.colonybridge.block.bridge.PreferredItems;
import org.tinycore.colonybridge.menu.AbstractGhostMenu;
import org.tinycore.colonybridge.menu.JoinedList;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.registry.ModBlocks;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * "Container" da tela da ponte. No Minecraft toda tela ligada a um bloco tem duas metades:
 * o menu (existe no servidor <b>e</b> no cliente) e a {@code Screen} (só no cliente, em {@code client/}).
 * <p>
 * Slots: 0..17 são os ghost slots do filtro, 18..35 os dos itens preferidos ({@link AbstractGhostMenu}),
 * e depois o inventário do jogador (só para pegar itens e clicar nos ghost slots). No cliente cada grupo
 * só aparece na sua aba ({@link BridgeTab}); o inventário, nas abas "Filtro" e "Preferidos".
 * <p>
 * O menu do servidor envia um {@link BridgeSnapshot} quando os dados mudam (checagem a cada
 * {@link #SNAPSHOT_INTERVAL_TICKS}); o do cliente guarda o último recebido para a tela desenhar.
 */
public class ColonyBridgeMenu extends AbstractGhostMenu {

    /** Frequência máxima de envio do snapshot: 1×/s. */
    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    /** Posições (relativas à tela) usadas também pela {@code ColonyBridgeScreen} para desenhar o fundo. */
    public static final int FILTER_X = 37;
    public static final int FILTER_Y = 80;
    public static final int INVENTORY_Y = 128;
    public static final int HOTBAR_Y = 186;

    /** Primeiro índice dos ghost slots de itens preferidos (logo depois dos do filtro). */
    public static final int PREFERRED_START = ItemFilter.SIZE;
    public static final int GHOST_COUNT = ItemFilter.SIZE + PreferredItems.SIZE;

    private final BlockPos pos;
    /** Só no servidor: block entity e acesso ao mundo para validar distância. */
    private final @Nullable ColonyBridgeBlockEntity bridge;
    private final ContainerLevelAccess access;
    private final Player player;

    private @Nullable BridgeSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido. */
    private BridgeSnapshot snapshot = BridgeSnapshot.EMPTY;
    /** Aba aberta: no cliente vem da tela; no servidor, do {@code BridgeTabPayload}. */
    private BridgeTab tab = BridgeTab.GENERAL;

    /** Construtor do servidor, chamado ao abrir a tela. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, ColonyBridgeBlockEntity bridge) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId,
                new JoinedList<>(bridge.getFilter().items(), bridge.getPreferred().items()), bridge::setChanged);
        this.pos = bridge.getBlockPos();
        this.bridge = bridge;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.player = inventory.player;
        addSlots(inventory);
    }

    /** Construtor do cliente: o servidor manda só a posição do bloco nos "dados extras" da abertura. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId,
                NonNullList.withSize(GHOST_COUNT, ItemStack.EMPTY), () -> {});
        this.pos = extraData.readBlockPos();
        this.bridge = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
        addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            addGhostSlot(i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18, () -> isVisible(BridgeTab.FILTER));
        }
        for (int i = 0; i < PreferredItems.SIZE; i++) {
            addGhostSlot(PREFERRED_START + i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18,
                    () -> isVisible(BridgeTab.PREFERRED));
        }
        addPlayerInventory(inventory, FILTER_X, INVENTORY_Y, HOTBAR_Y,
                () -> bridge != null || tab.showsInventory());
    }

    /** No servidor não existe "aba visível": os slots ficam sempre ativos; no cliente seguem a aba aberta. */
    private boolean isVisible(BridgeTab slotTab) {
        return bridge != null || tab == slotTab;
    }

    /** Shift-clique no inventário vai para o grupo de ghost slots da aba aberta. */
    @Override
    protected int quickMoveStart() {
        return tab == BridgeTab.PREFERRED ? PREFERRED_START : 0;
    }

    @Override
    protected int quickMoveEnd() {
        return switch (tab) {
            case FILTER -> ItemFilter.SIZE;
            case PREFERRED -> GHOST_COUNT;
            default -> 0;
        };
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

    /** Fecha a tela se o bloco sumiu ou o jogador se afastou mais de 8 blocos (validado no servidor). */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.COLONY_BRIDGE.get());
    }
}
