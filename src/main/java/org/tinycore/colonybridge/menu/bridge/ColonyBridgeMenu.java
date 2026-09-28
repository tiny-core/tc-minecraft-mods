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
import org.tinycore.colonybridge.menu.AbstractGhostMenu;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.registry.ModBlocks;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * "Container" da tela da ponte. No Minecraft toda tela ligada a um bloco tem duas metades:
 * o menu (existe no servidor <b>e</b> no cliente) e a {@code Screen} (só no cliente, em {@code client/}).
 * <p>
 * Slots: 0..17 são os ghost slots do filtro ({@link AbstractGhostMenu}), 18..53 o inventário do jogador
 * (só para pegar itens e clicar no filtro). Todos só aparecem na aba "Filtro".
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

    private final BlockPos pos;
    /** Só no servidor: block entity e acesso ao mundo para validar distância. */
    private final @Nullable ColonyBridgeBlockEntity bridge;
    private final ContainerLevelAccess access;
    private final Player player;

    private @Nullable BridgeSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido e se a aba "Filtro" está aberta. */
    private BridgeSnapshot snapshot = BridgeSnapshot.EMPTY;
    private boolean filterTabOpen;

    /** Construtor do servidor, chamado ao abrir a tela. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, ColonyBridgeBlockEntity bridge) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId, bridge.getFilter().items(), bridge::setChanged);
        this.pos = bridge.getBlockPos();
        this.bridge = bridge;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.player = inventory.player;
        addSlots(inventory);
    }

    /** Construtor do cliente: o servidor manda só a posição do bloco nos "dados extras" da abertura. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId,
                NonNullList.withSize(ItemFilter.SIZE, ItemStack.EMPTY), () -> {});
        this.pos = extraData.readBlockPos();
        this.bridge = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
        addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            addGhostSlot(i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18, this::isFilterTabOpen);
        }
        addPlayerInventory(inventory, FILTER_X, INVENTORY_Y, HOTBAR_Y, this::isFilterTabOpen);
    }

    @Override
    protected boolean canEditGhosts(Player player) {
        return bridge == null || bridge.canConfigure(player);
    }

    /** Nome antigo mantido para o pacote do JEI; hoje é o {@code setGhost} da base. */
    public void setFilterSlot(int slot, ItemStack stack, Player player) {
        setGhost(slot, stack, player);
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
        if (lastSent != null && lastSent.sameAs(current)) {
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

    /** No servidor não existe "aba": os slots ficam sempre ativos lá; no cliente seguem a aba aberta. */
    public boolean isFilterTabOpen() {
        return bridge != null || filterTabOpen;
    }

    /** Só no cliente: a tela avisa qual aba está aberta para mostrar/esconder os slots. */
    public void setFilterTabOpen(boolean open) {
        this.filterTabOpen = open;
    }

    /** Fecha a tela se o bloco sumiu ou o jogador se afastou mais de 8 blocos (validado no servidor). */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.COLONY_BRIDGE.get());
    }
}
