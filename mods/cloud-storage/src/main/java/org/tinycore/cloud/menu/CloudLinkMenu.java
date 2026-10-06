package org.tinycore.cloud.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.block.CloudLinkBlockEntity;
import org.tinycore.cloud.registry.ModMenus;
import org.tinycore.cloud.server.CloudInventory;
import org.tinycore.cloud.server.CloudService;

import java.util.UUID;

/**
 * Menu do TC Cloud Link. Os itens do canal <b>não</b> são slots: a grade é desenhada pela tela a partir do
 * {@link LinkView}, e cada clique vira um {@code LinkActionPayload} tratado por {@link #handleAction}. Slots de
 * verdade: só o inventário do jogador (shift-clique nele guarda o stack na nuvem, {@link #quickMoveStack}).
 *
 * <p>Servidor: guarda o block entity e o {@link CloudLinkSync}. Cliente: guarda o {@link LinkView}. A tela só
 * continua válida para o DONO do Link e a até 8 blocos dele.
 */
public class CloudLinkMenu extends AbstractContainerMenu {

    /**
     * Posições usadas também pela tela ({@code CloudLinkScreen}), para a grade com {@link #MIN_ROWS} linhas. A tela
     * mostra mais linhas (botão de altura) crescendo <b>para cima</b>, porque os slots têm posição fixa.
     */
    public static final int WIDTH = 190;
    public static final int GRID_X = 9;
    public static final int GRID_Y = 60;
    public static final int COLUMNS = 9;
    public static final int MIN_ROWS = 2;
    public static final int MAX_ROWS = 12;
    public static final int INVENTORY_X = (WIDTH - 9 * 18) / 2 + 1;
    public static final int INVENTORY_Y = GRID_Y + MIN_ROWS * 18 + 18;
    public static final int HOTBAR_Y = INVENTORY_Y + 58;
    public static final int HEIGHT = HOTBAR_Y + 24;
    private static final double MAX_DISTANCE_SQ = 8 * 8;
    private static final int PRIORITY_LIMIT = 1000;

    private final BlockPos pos;
    /** Só no servidor. */
    private final @Nullable CloudLinkBlockEntity link;
    /** Só no servidor. */
    private final @Nullable CloudLinkSync sync;
    /** Só no cliente. */
    private final LinkView view = new LinkView();

    /** Abre a tela para o dono (o bloco já conferiu que é ele). */
    public static void open(@NotNull ServerPlayer player, @NotNull CloudLinkBlockEntity link) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CloudLinkMenu(id, inventory, link),
                Component.translatable("block.tccloud.cloud_link")), buf -> buf.writeBlockPos(link.getBlockPos()));
    }

    /** Servidor. */
    public CloudLinkMenu(int containerId, Inventory inventory, @NotNull CloudLinkBlockEntity link) {
        this(containerId, inventory, link.getBlockPos(), link);
    }

    /** Cliente: recebe a posição escrita em {@link #open}. */
    public CloudLinkMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null);
    }

    private CloudLinkMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable CloudLinkBlockEntity link) {
        super(ModMenus.CLOUD_LINK.get(), containerId);
        this.pos = pos;
        this.link = link;
        this.sync = link != null && inventory.player instanceof ServerPlayer sp ? new CloudLinkSync(sp, containerId, link) : null;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    public @NotNull LinkView view() {
        return view;
    }

    @Override
    public boolean stillValid(Player player) {
        if (link == null) return true; // cliente: o servidor decide e fecha a tela
        return !link.isRemoved() && link.isOwner(player)
                && player.distanceToSqr(pos.getCenter()) <= MAX_DISTANCE_SQ;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (sync != null) sync.tick();
    }

    /** Pedido da tela, já validado pelo {@code ModNetwork} (menu certo, aberto e válido). */
    public void handleAction(@NotNull ServerPlayer player, @NotNull LinkAction action, @NotNull String fingerprint) {
        if (link == null || sync == null) return;
        switch (action) {
            case CYCLE_ACCESS -> link.setAccess(link.access().next());
            case PRIORITY_UP -> link.setPriority(Math.min(PRIORITY_LIMIT, link.priority() + 1));
            case PRIORITY_DOWN -> link.setPriority(Math.max(-PRIORITY_LIMIT, link.priority() - 1));
            default -> moveItems(player, action, fingerprint);
        }
        sync.soon();
    }

    private void moveItems(ServerPlayer player, LinkAction action, String fingerprint) {
        CloudService service = CloudService.get();
        UUID channel = service == null ? null : service.defaultChannel(player.getUUID());
        if (service == null || channel == null) return;
        CloudInventory inventory = service.inventory();
        switch (action) {
            case TAKE_STACK -> CloudLinkActions.take(player, inventory, channel, fingerprint, 64);
            case TAKE_ONE -> CloudLinkActions.take(player, inventory, channel, fingerprint, 1);
            case DEPOSIT_CARRIED, DEPOSIT_ONE -> {
                ItemStack carried = getCarried();
                if (carried.isEmpty()) return;
                int amount = action == LinkAction.DEPOSIT_ONE ? 1 : carried.getCount();
                setCarried(deposit(player, inventory, channel, carried, amount));
            }
            default -> { }
        }
    }

    /** Guarda {@code amount} de {@code stack} e devolve o que sobrou. */
    private ItemStack deposit(ServerPlayer player, CloudInventory inventory, UUID channel, ItemStack stack, int amount) {
        CloudInventory.InsertResult result = inventory.insert(player.getUUID(), channel, stack, amount, false);
        if (sync != null) sync.setRejection(result.rejection());
        ItemStack rest = stack.copy();
        rest.shrink((int) result.accepted());
        return rest;
    }

    /** Shift-clique num slot do inventário: guarda o stack inteiro na nuvem (só no servidor de verdade). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer sp) || link == null || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        CloudService service = CloudService.get();
        UUID channel = service == null ? null : service.defaultChannel(sp.getUUID());
        if (!slot.hasItem() || service == null || channel == null) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        slot.set(deposit(sp, service.inventory(), channel, stack, stack.getCount()));
        if (sync != null) sync.soon();
        return ItemStack.EMPTY; // nada para repetir: o Minecraft chamaria de novo enquanto houver retorno
    }
}
