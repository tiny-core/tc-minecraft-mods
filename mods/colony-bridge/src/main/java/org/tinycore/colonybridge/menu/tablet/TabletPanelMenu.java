package org.tinycore.colonybridge.menu.tablet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.monitor.MonitorData;
import org.tinycore.colonybridge.block.monitor.MonitorSource;
import org.tinycore.colonybridge.menu.access.MenuAccess;
import org.tinycore.colonybridge.network.TabletPanelPayload;
import org.tinycore.colonybridge.registry.ModMenus;

/**
 * Aba de painel do tablet: mostra o que um Monitor da Colônia mostraria de um bloco (Ponte ou Abastecedor), sem
 * precisar de monitor. Menu sem slots: o servidor manda os mesmos dados do monitor ({@link MonitorSource#monitorData})
 * a cada segundo, só quando mudam ({@link TabletPanelPayload}); o cliente guarda o último para a tela desenhar.
 * <p>
 * Continua aberto pelas mesmas regras do tablet ({@link MenuAccess}, com {@code TabletAccess}).
 */
public class TabletPanelMenu extends AbstractContainerMenu implements TabletMenu {

    private static final int SYNC_TICKS = 20;

    /** Só no servidor. */
    private final @Nullable MonitorSource source;
    private final MenuAccess access;
    private final @Nullable TabletView tabletView;
    private final Player player;
    private @Nullable MonitorData lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último dado recebido. */
    private MonitorData data = MonitorData.MISSING;

    /** Servidor: aberto pelo {@code TabletOpener}. */
    public TabletPanelMenu(int containerId, Inventory inventory, MonitorSource source, MenuAccess access) {
        super(ModMenus.TABLET_PANEL.get(), containerId);
        this.source = source;
        this.access = access;
        this.tabletView = access.tabletView();
        this.player = inventory.player;
    }

    /** Cliente: os dados extras trazem só as abas do tablet. */
    public TabletPanelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.TABLET_PANEL.get(), containerId);
        this.source = null;
        this.access = MenuAccess.CLIENT;
        this.tabletView = TabletView.read(extraData);
        this.player = inventory.player;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (source == null || !(player instanceof ServerPlayer serverPlayer) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_TICKS;
        MonitorData current = source.monitorData();
        if (current.equals(lastSent)) {
            return;
        }
        lastSent = current;
        PacketDistributor.sendToPlayer(serverPlayer,
                new TabletPanelPayload(containerId, current.save(serverPlayer.registryAccess())));
    }

    public MonitorData getData() {
        return data;
    }

    public void setData(MonitorData data) {
        this.data = data;
    }

    @Override
    public @Nullable TabletView tabletView() {
        return tabletView;
    }

    /** Sem slots: shift-clique não faz nada. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.stillValid(player);
    }
}
