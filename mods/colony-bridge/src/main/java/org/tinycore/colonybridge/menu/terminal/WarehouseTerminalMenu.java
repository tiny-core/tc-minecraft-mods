package org.tinycore.colonybridge.menu.terminal;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
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
 * {@link #handleAction}. Slots de verdade: a bancada 3×3 ({@link TerminalCrafting}), o resultado e o
 * inventário do jogador — nessa ordem.
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
    public static final int CRAFT_Y = 154;
    public static final int RESULT_X = GRID_X + 92;
    public static final int RESULT_Y = CRAFT_Y + 18;
    public static final int INVENTORY_Y = 224;
    public static final int HOTBAR_Y = 282;

    /** Índices dos slots no menu. */
    private static final int RESULT_SLOT = TerminalCrafting.SIZE;
    private static final int INVENTORY_START = RESULT_SLOT + 1;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    private final String colonyName;
    private final TerminalCrafting crafting;
    /** Só no servidor. */
    private final @Nullable WarehouseSync sync;
    /** Só no cliente. */
    private final WarehouseView view = new WarehouseView();
    /** Quanto já saiu do resultado neste shift-clique (limita a um stack, como no AE2). */
    private int quickCrafted;

    /** Servidor: criado quando o jogador abre o bloco. */
    public WarehouseTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, ColonyAccess.colonyNameAt(inventory.player.level(), pos),
                ContainerLevelAccess.create(inventory.player.level(), pos));
    }

    /** Cliente: recebe a posição e o nome da colônia escritos por {@link #writeOpenData}. */
    public WarehouseTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readUtf(64), ContainerLevelAccess.NULL);
    }

    private WarehouseTerminalMenu(int containerId, Inventory inventory, BlockPos pos, String colonyName,
                                  ContainerLevelAccess access) {
        super(ModMenus.WAREHOUSE_TERMINAL.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.access = access;
        this.colonyName = colonyName;
        this.sync = player instanceof ServerPlayer serverPlayer ? new WarehouseSync(serverPlayer, containerId) : null;
        this.crafting = new TerminalCrafting(this, player);
        for (int i = 0; i < TerminalCrafting.SIZE; i++) {
            addSlot(new Slot(crafting.grid, i, GRID_X + (i % 3) * 18, CRAFT_Y + (i / 3) * 18));
        }
        addSlot(new TerminalResultSlot(this, crafting, player, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, GRID_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, GRID_X + column * 18, HOTBAR_Y));
        }
    }

    /** Dados mandados ao cliente junto com o pedido de abrir a tela. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, String colonyName) {
        buf.writeBlockPos(pos);
        buf.writeUtf(colonyName, 64);
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
        if (action == TerminalAction.CLEAR_GRID) {
            crafting.clearTo(racks);
        } else {
            TerminalActions.apply(this, player, racks, action, item);
        }
        scanSoon();
    }

    /** Receita do JEI, já validada pelo {@code TerminalPackets}. */
    public void fillRecipe(List<IItemHandler> racks, List<List<ItemStack>> options, boolean max) {
        crafting.fillFromRecipe(racks, options, max);
        scanSoon();
    }

    private void scanSoon() {
        if (sync != null) {
            sync.scanSoon();
        }
    }

    /** A grade da bancada mudou: recalcula o resultado (só faz algo no servidor). */
    @Override
    public void slotsChanged(Container container) {
        if (container == crafting.grid) {
            crafting.updateResult(RESULT_SLOT);
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

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        quickCrafted = 0;
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Shift-clique: no inventário, guarda o stack no armazém (como no terminal do AE2); na grade da bancada,
     * volta para o inventário; no resultado, crafta direto para o inventário, repetindo até um stack.
     * Devolver vazio faz o jogo parar de repetir; devolver o item faz ele tentar de novo.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (index == RESULT_SLOT) {
            return quickCraft(player, slot);
        }
        if (index < RESULT_SLOT) {
            moveItemStackTo(slot.getItem(), INVENTORY_START, INVENTORY_END, false);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        List<IItemHandler> racks = racks(); // no cliente é null: o servidor faz e corrige o slot
        if (racks != null) {
            slot.set(WarehouseItems.insert(racks, slot.getItem()));
            scanSoon();
        }
        return ItemStack.EMPTY;
    }

    /** Mesmo fluxo do shift-clique no resultado da bancada vanilla, com o teto de um stack por clique. */
    private ItemStack quickCraft(Player player, Slot slot) {
        ItemStack output = slot.getItem();
        if (quickCrafted >= output.getMaxStackSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = output.copy();
        output.getItem().onCraftedBy(output, player.level(), player);
        if (!moveItemStackTo(output, INVENTORY_START, INVENTORY_END, true)) {
            return ItemStack.EMPTY;
        }
        slot.onQuickCraft(output, copy);
        slot.setChanged();
        if (output.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        quickCrafted += copy.getCount();
        slot.onTake(player, output);
        return copy;
    }

    /** Duplo clique ("juntar tudo") não puxa do resultado da bancada. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != crafting.result && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Ao fechar: a grade volta para o armazém (o que não couber, para o inventário ou o chão). A grade não
     * fica guardada no bloco — ele não tem block entity.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        List<IItemHandler> racks = racks();
        if (racks != null) {
            crafting.clearTo(racks);
        }
        access.execute((level, blockPos) -> clearContainer(player, crafting.grid));
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
