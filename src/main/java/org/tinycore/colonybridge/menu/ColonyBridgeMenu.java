package org.tinycore.colonybridge.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.block.ItemFilter;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.registry.ModMenus;
import org.tinycore.colonybridge.registry.ModBlocks;

/**
 * "Container" da tela da ponte. No Minecraft toda tela ligada a um bloco tem duas metades:
 * o menu (existe no servidor <b>e</b> no cliente) e a {@code Screen} (só no cliente, em {@code client/}).
 * <p>
 * Slots: 0..17 são os ghost slots do filtro ({@link GhostSlot}), 18..53 o inventário do jogador
 * (só para pegar itens e clicar no filtro). Todos só aparecem na aba "Filtro".
 * <p>
 * O menu do servidor envia um {@link BridgeSnapshot} quando os dados mudam (checagem a cada
 * {@link #SNAPSHOT_INTERVAL_TICKS}); o do cliente guarda o último recebido para a tela desenhar.
 */
public class ColonyBridgeMenu extends AbstractContainerMenu {

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
    private final GhostContainer filter;

    private @Nullable BridgeSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido e se a aba "Filtro" está aberta. */
    private BridgeSnapshot snapshot = BridgeSnapshot.EMPTY;
    private boolean filterTabOpen;

    /** Construtor do servidor, chamado ao abrir a tela. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, ColonyBridgeBlockEntity bridge) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId);
        this.pos = bridge.getBlockPos();
        this.bridge = bridge;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.player = inventory.player;
        this.filter = new GhostContainer(bridge.getFilter().items(), bridge::setChanged);
        addSlots(inventory);
    }

    /** Construtor do cliente: o servidor manda só a posição do bloco nos "dados extras" da abertura. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId);
        this.pos = extraData.readBlockPos();
        this.bridge = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
        this.filter = new GhostContainer(NonNullList.withSize(ItemFilter.SIZE, ItemStack.EMPTY), () -> {});
        addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + (i % 9) * 18, FILTER_Y + (i / 9) * 18, this::isFilterTabOpen));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new TabSlot(inventory, 9 + row * 9 + col, FILTER_X + col * 18, INVENTORY_Y + row * 18,
                        this::isFilterTabOpen));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new TabSlot(inventory, col, FILTER_X + col * 18, HOTBAR_Y, this::isFilterTabOpen));
        }
    }

    // ---------------------------------------------------------------- filtro (ghost slots)

    /**
     * Intercepta cliques nos ghost slots antes da lógica padrão do Minecraft (que moveria itens):
     * clique normal copia 1 unidade do item do cursor (ou limpa, com a mão vazia); shift-clique limpa.
     * Qualquer outro tipo de clique (número, soltar, clonar) é ignorado. O item do cursor nunca muda.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId < 0 || slotId >= ItemFilter.SIZE) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (clickType == ClickType.PICKUP) {
            setFilterSlot(slotId, getCarried(), player);
        } else if (clickType == ClickType.QUICK_MOVE) {
            setFilterSlot(slotId, ItemStack.EMPTY, player);
        }
    }

    /** Shift-clique no inventário: copia o item para o primeiro slot livre do filtro. Nunca move itens. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < ItemFilter.SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < ItemFilter.SIZE; i++) {
            if (filter.getItem(i).isEmpty()) {
                setFilterSlot(i, stack, player);
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Grava uma cópia (1 unidade) no slot do filtro. No servidor, confere de novo a permissão
     * (ela pode ter sido retirada com a tela aberta). Chamado também pelo pacote do JEI, já validado.
     */
    public void setFilterSlot(int slot, ItemStack stack, Player player) {
        if (slot < 0 || slot >= ItemFilter.SIZE) {
            return;
        }
        if (bridge != null && !bridge.canConfigure(player)) {
            return;
        }
        filter.setItem(slot, stack);
    }

    /** Duplo clique juntando itens nunca puxa dos ghost slots. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /** Arrastar o cursor espalhando itens nunca coloca nos ghost slots. */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canDragTo(slot);
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
