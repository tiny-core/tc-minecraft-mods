package org.tinycore.colonybridge.menu.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.logic.terminal.TerminalAction;
import org.tinycore.colonybridge.logic.warehouse.WarehouseItems;
import org.tinycore.colonybridge.registry.ModBlocks;
import org.tinycore.colonybridge.registry.ModMenus;

import java.util.List;

/**
 * Menu do Terminal do Armazém. Os itens do armazém <b>não</b> são slots (seriam milhares): a grade é
 * desenhada pela tela a partir do {@link WarehouseView}, e cada clique vira um pacote tratado por
 * {@link #handleAction}. Slots de verdade só o inventário do jogador.
 * <p>
 * Servidor: guarda a posição do bloco e o {@link WarehouseSync}, que manda o conteúdo dos racks ao cliente.
 * Cliente: guarda o {@link WarehouseView} e o nome da colônia, recebido na abertura ("dados extras").
 * A permissão da colônia é conferida de novo a cada uso ({@link #racks}); o bloco não guarda dono.
 */
public class WarehouseTerminalMenu extends AbstractContainerMenu {

    /** Posições usadas também pela tela ({@code WarehouseTerminalScreen}). */
    public static final int GRID_X = 9;
    public static final int GRID_Y = 48;
    public static final int COLUMNS = 9;
    public static final int ROWS = 5;
    public static final int INVENTORY_Y = 162;
    public static final int HOTBAR_Y = 220;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    private final String colonyName;
    /** Só no servidor. */
    private final @Nullable WarehouseSync sync;
    /** Só no cliente. */
    private final WarehouseView view = new WarehouseView();

    /** Servidor: criado quando o jogador abre o bloco. */
    public WarehouseTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenus.WAREHOUSE_TERMINAL.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(player.level(), pos);
        this.colonyName = ColonyAccess.colonyNameAt(player.level(), pos);
        this.sync = player instanceof ServerPlayer serverPlayer ? new WarehouseSync(serverPlayer, containerId) : null;
        addPlayerInventory(inventory);
    }

    /** Cliente: recebe a posição e o nome da colônia escritos por {@link #writeOpenData}. */
    public WarehouseTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.WAREHOUSE_TERMINAL.get(), containerId);
        this.pos = extraData.readBlockPos();
        this.colonyName = extraData.readUtf(64);
        this.player = inventory.player;
        this.access = ContainerLevelAccess.NULL;
        this.sync = null;
        addPlayerInventory(inventory);
    }

    /** Dados mandados ao cliente junto com o pedido de abrir a tela. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, String colonyName) {
        buf.writeBlockPos(pos);
        buf.writeUtf(colonyName, 64);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, GRID_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, GRID_X + column * 18, HOTBAR_Y));
        }
    }

    /**
     * Racks do armazém, só no servidor e só se o jogador ainda pode usar a colônia.
     * @return null no cliente, fora de colônia ou sem permissão
     */
    public @Nullable List<IItemHandler> racks() {
        if (sync == null) {
            return null;
        }
        return ColonyAccess.accessibleRacks(player.level(), pos, player.getUUID());
    }

    /** Clique na grade, já validado pelo {@code TerminalPackets} (menu certo, distância, permissão). */
    public void handleAction(ServerPlayer player, List<IItemHandler> racks, TerminalAction action, ItemStack item) {
        TerminalActions.apply(this, player, racks, action, item);
        if (sync != null) {
            sync.scanSoon();
        }
    }

    /** Servidor, todo tick: o {@link WarehouseSync} decide se é hora de ler os racks. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (sync == null) {
            return;
        }
        sync.tick(() -> {
            List<IItemHandler> racks = racks();
            return racks == null ? List.of() : racks; // sem permissão/colônia: a grade fica vazia
        });
    }

    /**
     * Shift-clique num slot do inventário: guarda o stack inteiro no armazém (como no terminal do AE2).
     * Devolve vazio para o jogo não repetir o movimento. No cliente não faz nada: o servidor corrige o slot.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        List<IItemHandler> racks = racks();
        if (slot == null || !slot.hasItem() || racks == null) {
            return ItemStack.EMPTY;
        }
        slot.set(WarehouseItems.insert(racks, slot.getItem()));
        if (sync != null) {
            sync.scanSoon();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.WAREHOUSE_TERMINAL.get());
    }

    public WarehouseView getView() {
        return view;
    }

    public String getColonyName() {
        return colonyName;
    }

    public BlockPos getPos() {
        return pos;
    }
}
