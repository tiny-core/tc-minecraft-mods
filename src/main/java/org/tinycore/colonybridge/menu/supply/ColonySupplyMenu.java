package org.tinycore.colonybridge.menu.supply;

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
import org.tinycore.colonybridge.block.supply.ColonySupplyBlockEntity;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.menu.AbstractGhostMenu;
import org.tinycore.colonybridge.network.SupplySnapshotPayload;
import org.tinycore.colonybridge.registry.ModBlocks;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * Menu do bloco de abastecimento. Slots 0..8 são as linhas "manter no armazém", 9..17 as de
 * "excedente para o ME" e o resto é o inventário do jogador. Todos sempre visíveis (a tela não tem abas).
 * <p>
 * Igual à tela da ponte, o servidor envia um {@link SupplySnapshot} no máximo 1×/s e só quando muda.
 */
public class ColonySupplyMenu extends AbstractGhostMenu {

    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    /** Posições usadas também pela {@code ColonySupplyScreen} para desenhar o fundo dos slots. */
    public static final int LIST_X = 37;
    public static final int KEEP_Y = 66;
    public static final int SURPLUS_Y = 100;
    public static final int INVENTORY_Y = 134;
    public static final int HOTBAR_Y = 192;

    private final BlockPos pos;
    /** Só no servidor: block entity e acesso ao mundo para validar distância. */
    private final @Nullable ColonySupplyBlockEntity supply;
    private final ContainerLevelAccess access;
    private final Player player;

    private @Nullable SupplySnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido. */
    private SupplySnapshot snapshot = SupplySnapshot.EMPTY;

    public ColonySupplyMenu(int containerId, Inventory inventory, ColonySupplyBlockEntity supply) {
        super(ModMenus.COLONY_SUPPLY.get(), containerId, supply.getStock().items(), supply::setChanged);
        this.pos = supply.getBlockPos();
        this.supply = supply;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.player = inventory.player;
        addSlots(inventory);
    }

    public ColonySupplyMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_SUPPLY.get(), containerId,
                NonNullList.withSize(StockList.SIZE, ItemStack.EMPTY), () -> {});
        this.pos = extraData.readBlockPos();
        this.supply = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
        addSlots(inventory);
    }

    private void addSlots(Inventory inventory) {
        for (int i = 0; i < StockList.SIZE; i++) {
            int row = StockList.isKeep(i) ? 0 : 1;
            int column = StockList.isKeep(i) ? i : i - StockList.KEEP_SLOTS;
            addGhostSlot(i, LIST_X + column * 18, (row == 0 ? KEEP_Y : SURPLUS_Y), () -> true);
        }
        addPlayerInventory(inventory, LIST_X, INVENTORY_Y, HOTBAR_Y, () -> true);
    }

    @Override
    protected boolean canEditGhosts(Player player) {
        return supply == null || supply.canConfigure(player);
    }

    /** Linha preenchida agora: já começa com uma quantidade alvo útil, senão ela ficaria em zero (inativa). */
    @Override
    protected void onGhostSet(int slot, ItemStack stack) {
        if (supply == null) {
            return;
        }
        if (stack.isEmpty()) {
            supply.setAmount(slot, 0);
        } else if (supply.getStock().amount(slot) <= 0) {
            supply.setAmount(slot, StockList.DEFAULT_AMOUNT);
        }
        requestSync();
    }

    // ---------------------------------------------------------------- sincronização

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (supply == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (--ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SNAPSHOT_INTERVAL_TICKS;
        SupplySnapshot current = supply.snapshot();
        if (current.equals(lastSent)) {
            return;
        }
        lastSent = current;
        PacketDistributor.sendToPlayer(serverPlayer, new SupplySnapshotPayload(containerId, current));
    }

    public void requestSync() {
        ticksUntilSync = 0;
    }

    /** Só no servidor: o bloco desta tela (null no cliente). */
    public @Nullable ColonySupplyBlockEntity getSupply() {
        return supply;
    }

    public BlockPos getPos() {
        return pos;
    }

    public SupplySnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(SupplySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.COLONY_SUPPLY.get());
    }
}
