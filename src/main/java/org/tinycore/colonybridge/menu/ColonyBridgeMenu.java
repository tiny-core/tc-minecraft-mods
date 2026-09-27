package org.tinycore.colonybridge.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.network.BridgeSnapshotPayload;
import org.tinycore.colonybridge.registry.ModMenus;
import org.tinycore.colonybridge.registry.ModRegistries;

/**
 * "Container" da tela da ponte. No Minecraft toda tela ligada a um bloco tem duas metades:
 * o menu (existe no servidor <b>e</b> no cliente) e a {@code Screen} (só no cliente, em {@code client/}).
 * <p>
 * Esta ponte não tem slots. O menu do servidor envia um {@link BridgeSnapshot} ao jogador quando os
 * dados mudam (checagem a cada {@link #SNAPSHOT_INTERVAL_TICKS}); o menu do cliente só guarda o último
 * snapshot recebido para a tela desenhar.
 */
public class ColonyBridgeMenu extends AbstractContainerMenu {

    /** Frequência máxima de envio do snapshot: 1×/s. */
    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    private final BlockPos pos;
    /** Só no servidor: block entity e acesso ao mundo para validar distância. */
    private final @Nullable ColonyBridgeBlockEntity bridge;
    private final ContainerLevelAccess access;
    private final Player player;

    private @Nullable BridgeSnapshot lastSent;
    private int ticksUntilSync;
    /** Só no cliente: último snapshot recebido. */
    private BridgeSnapshot snapshot = BridgeSnapshot.EMPTY;

    /** Construtor do servidor, chamado ao abrir a tela. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, ColonyBridgeBlockEntity bridge) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId);
        this.pos = bridge.getBlockPos();
        this.bridge = bridge;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.player = inventory.player;
    }

    /** Construtor do cliente: o servidor manda só a posição do bloco nos "dados extras" da abertura. */
    public ColonyBridgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.COLONY_BRIDGE.get(), containerId);
        this.pos = extraData.readBlockPos();
        this.bridge = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
    }

    /**
     * Chamado pelo servidor todo tick enquanto a tela está aberta. Monta o snapshot a cada
     * segundo e só envia se algo mudou.
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

    /** Fecha a tela se o bloco sumiu ou o jogador se afastou mais de 8 blocos (validado no servidor). */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistries.COLONY_BRIDGE.get());
    }

    /** Sem slots: não há shift-clique para tratar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
