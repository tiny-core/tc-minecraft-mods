package org.tinycore.colonybridge.menu.encoder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.encoder.PatternEncoderBlockEntity;
import org.tinycore.colonybridge.integration.ae2.PatternEncoding;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.menu.tablet.TabletView;
import org.tinycore.colonybridge.network.EncoderSnapshotPayload;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * Menu do TC Pattern Encoder. Slots de verdade: Blank Patterns (1), saída (9) e o inventário do jogador, nessa
 * ordem. A lista de pedidos não é slot: vem no {@link EncoderSnapshot}, mandado 1×/s só quando muda; o botão
 * "codificar" vai pelo {@code EncoderActionPayload}.
 * <p>
 * Os slots de saída só deixam tirar (nada entra neles pela tela); o de Blank Pattern só aceita Blank Pattern.
 */
public class PatternEncoderMenu extends AbstractContainerMenu {

    /** Posições usadas também pela tela ({@code PatternEncoderScreen}). */
    public static final int WIDTH = 222;
    public static final int LIST_Y = 34;
    public static final int ROW_HEIGHT = 20;
    public static final int VISIBLE_ROWS = 5;
    public static final int SLOTS_Y = LIST_Y + ROW_HEIGHT * VISIBLE_ROWS + 16;
    public static final int BLANK_X = 13;
    public static final int OUTPUT_X = WIDTH - 9 - 9 * 18;
    public static final int INVENTORY_X = 31;
    public static final int INVENTORY_Y = SLOTS_Y + 34;
    public static final int HOTBAR_Y = INVENTORY_Y + 58;
    public static final int HEIGHT = HOTBAR_Y + 24;

    private static final int OUTPUTS = 9;
    private static final int OUTPUT_START = 1;
    private static final int INVENTORY_START = OUTPUT_START + OUTPUTS;
    private static final int INVENTORY_END = INVENTORY_START + 36;
    private static final int SYNC_TICKS = 20;

    /** Só no servidor. */
    private final @Nullable PatternEncoderBlockEntity encoder;
    private final MenuAccess access;
    private final Player player;
    private @Nullable EncoderSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente. */
    private EncoderSnapshot snapshot = EncoderSnapshot.EMPTY;

    /** Servidor: aberto pelo bloco. */
    public PatternEncoderMenu(int containerId, Inventory inventory, PatternEncoderBlockEntity encoder, MenuAccess access) {
        this(containerId, inventory, encoder, access, encoder.blanks(), encoder.outputs());
    }

    /** Cliente: dados extras = posição do bloco + abas do tablet (sempre vazias aqui). */
    public PatternEncoderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, null, MenuAccess.CLIENT, new ItemStackHandler(1), new ItemStackHandler(OUTPUTS));
        extraData.readBlockPos();
        TabletView.read(extraData);
    }

    private PatternEncoderMenu(int containerId, Inventory inventory, @Nullable PatternEncoderBlockEntity encoder,
                               MenuAccess access, IItemHandler blanks, IItemHandler outputs) {
        super(ModMenus.PATTERN_ENCODER.get(), containerId);
        this.encoder = encoder;
        this.access = access;
        this.player = inventory.player;
        addSlot(new SlotItemHandler(blanks, 0, BLANK_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PatternEncoding.isBlankPattern(stack);
            }
        });
        for (int i = 0; i < OUTPUTS; i++) {
            addSlot(new SlotItemHandler(outputs, i, OUTPUT_X + i * 18, SLOTS_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false; // saída: só tira
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, HOTBAR_Y));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (encoder == null || !(player instanceof ServerPlayer serverPlayer)
                || !(encoder.getLevel() instanceof ServerLevel level) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_TICKS;
        EncoderSnapshot current = encoder.snapshot(level);
        if (lastSent == null || !current.sameAs(lastSent)) {
            lastSent = current;
            PacketDistributor.sendToPlayer(serverPlayer, new EncoderSnapshotPayload(containerId, current));
        }
    }

    /** Força o envio no próximo tick (logo depois de codificar). */
    public void requestSync() {
        ticksUntilSync = 0;
    }

    /** Só no servidor. */
    public @Nullable PatternEncoderBlockEntity getEncoder() {
        return encoder;
    }

    public EncoderSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(EncoderSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Shift-clique: Blank Pattern do inventário vai para o slot de entrada; o que está nos slots do bloco vai para o
     * inventário. O resto não se move.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < INVENTORY_START
                ? moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)
                : PatternEncoding.isBlankPattern(stack) && moveItemStackTo(stack, 0, OUTPUT_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.stillValid(player);
    }
}
