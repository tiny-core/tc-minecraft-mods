package org.tinycore.colonybridge.menu.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.loader.ColonyChunkLoaderBlockEntity;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.menu.tablet.TabletMenu;
import org.tinycore.colonybridge.menu.tablet.TabletView;
import org.tinycore.colonybridge.network.ChunkLoaderSnapshotPayload;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * Menu do Chunk Loader (sem slots): o servidor manda a situação ({@link ChunkLoaderSnapshot}) 1×/s, só quando
 * muda; os botões (liga/desliga, redstone) vão pelo {@code ChunkLoaderActionPayload}. Abre pelo bloco ou pelo
 * tablet ({@link MenuAccess}).
 */
public class ChunkLoaderMenu extends AbstractContainerMenu implements TabletMenu {

    private static final int SYNC_TICKS = 20;

    /** Só no servidor. */
    private final @Nullable ColonyChunkLoaderBlockEntity loader;
    private final MenuAccess access;
    private final @Nullable TabletView tabletView;
    private final Player player;
    private @Nullable ChunkLoaderSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente. */
    private ChunkLoaderSnapshot snapshot = ChunkLoaderSnapshot.EMPTY;

    public ChunkLoaderMenu(int containerId, Inventory inventory, ColonyChunkLoaderBlockEntity loader, MenuAccess access) {
        super(ModMenus.CHUNK_LOADER.get(), containerId);
        this.loader = loader;
        this.access = access;
        this.tabletView = access.tabletView();
        this.player = inventory.player;
    }

    /** Cliente: dados extras = posição do bloco + abas do tablet. */
    public ChunkLoaderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.CHUNK_LOADER.get(), containerId);
        extraData.readBlockPos();
        this.loader = null;
        this.access = MenuAccess.CLIENT;
        this.tabletView = TabletView.read(extraData);
        this.player = inventory.player;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (loader == null || !(player instanceof ServerPlayer serverPlayer) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_TICKS;
        ChunkLoaderSnapshot current = loader.snapshot();
        if (!current.equals(lastSent)) {
            lastSent = current;
            PacketDistributor.sendToPlayer(serverPlayer, new ChunkLoaderSnapshotPayload(containerId, current));
        }
    }

    /** Força o envio no próximo tick (logo depois de um botão). */
    public void requestSync() {
        ticksUntilSync = 0;
    }

    /** Só no servidor. */
    public @Nullable ColonyChunkLoaderBlockEntity getLoader() {
        return loader;
    }

    public ChunkLoaderSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(ChunkLoaderSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public @Nullable TabletView tabletView() {
        return tabletView;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.stillValid(player);
    }
}
