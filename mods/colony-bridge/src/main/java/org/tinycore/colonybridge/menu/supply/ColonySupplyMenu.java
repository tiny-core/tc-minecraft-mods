package org.tinycore.colonybridge.menu.supply;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.supply.ColonySupplyBlockEntity;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.logic.target.TargetListHost;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.TargetListEditor;
import org.tinycore.colonybridge.menu.TargetListMenu;
import org.tinycore.colonybridge.menu.TargetListSync;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.menu.tablet.TabletMenu;
import org.tinycore.colonybridge.menu.tablet.TabletView;
import org.tinycore.colonybridge.network.SupplySnapshotPayload;
import org.tinycore.colonybridge.registry.ModMenus;
import org.tinycore.core.menu.AbstractGhostMenu;

import java.util.List;

/**
 * Menu do Abastecedor. As duas listas (Manter e Excedente) não são slots: a tela as desenha como listas de
 * linhas e cada edição vai por pacote ({@code TargetEditPayload}); os slots do menu são só o inventário do
 * jogador (para pegar itens e soltá-los nos ícones das linhas).
 * <p>
 * Estende {@link AbstractGhostMenu} sem ghost slots, só para reaproveitar o inventário padrão das telas do mod.
 * O servidor envia um {@link SupplySnapshot} no máximo 1×/s e só quando muda, e as linhas das listas quando
 * elas mudam ({@link TargetListSync}).
 */
public class ColonySupplyMenu extends AbstractGhostMenu implements TargetListMenu, TabletMenu {

    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    /** Posições usadas também pela {@code ColonySupplyScreen}. */
    public static final int INVENTORY_X = 20; // 9 colunas centralizadas na janela de 202 px
    public static final int INVENTORY_Y = 179;
    public static final int HOTBAR_Y = 237;

    private final BlockPos pos;
    /** Só no servidor: o block entity. */
    private final @Nullable ColonySupplyBlockEntity supply;
    /** Por onde a tela foi aberta (bloco ou tablet): decide quando ela continua válida. */
    private final MenuAccess access;
    private final @Nullable TabletView tabletView;
    private final Player player;
    private final TargetListSync lists = new TargetListSync(TargetListKind.KEEP, TargetListKind.SURPLUS);
    /** Lista da aba aberta (servidor: vem da tela), destino do shift-clique. */
    private TargetListKind activeList = TargetListKind.KEEP;

    private @Nullable SupplySnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido. */
    private SupplySnapshot snapshot = SupplySnapshot.EMPTY;

    public ColonySupplyMenu(int containerId, Inventory inventory, ColonySupplyBlockEntity supply, MenuAccess access) {
        super(ModMenus.COLONY_SUPPLY.get(), containerId, List.of(), supply::setChanged);
        this.pos = supply.getBlockPos();
        this.supply = supply;
        this.access = access;
        this.tabletView = access.tabletView();
        this.player = inventory.player;
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y, HOTBAR_Y, () -> true);
    }

    public ColonySupplyMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_SUPPLY.get(), containerId, List.of(), () -> {});
        this.pos = extraData.readBlockPos();
        this.supply = null;
        this.access = MenuAccess.CLIENT;
        this.tabletView = TabletView.read(extraData);
        this.player = inventory.player;
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y, HOTBAR_Y, () -> true);
    }

    @Override
    protected boolean canEditGhosts(Player player) {
        return supply == null || supply.canConfigure(player);
    }

    /** Shift-clique no inventário: vira uma linha nova na lista da aba aberta. Nunca move o item. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (supply != null && index >= 0 && index < slots.size() && canEditGhosts(player)) {
            TargetList list = supply.targetList(activeList);
            if (list != null && TargetListEditor.addStack(list, slots.get(index).getItem())) {
                supply.onTargetListChanged(activeList);
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- sincronização

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (supply == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        lists.sendChanged(serverPlayer, containerId, supply);
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

    @Override
    public TargetListSync targetLists() {
        return lists;
    }

    @Override
    public @Nullable TargetListHost listHost() {
        return supply;
    }

    @Override
    public void setActiveList(TargetListKind kind) {
        if (kind == TargetListKind.KEEP || kind == TargetListKind.SURPLUS) {
            activeList = kind;
        }
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
        return access.stillValid(player);
    }

    @Override
    public @Nullable TabletView tabletView() {
        return tabletView;
    }

}
