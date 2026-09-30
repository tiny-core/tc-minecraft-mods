package org.tinycore.colonybridge.menu.access;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.item.ColonyTabletItem;
import org.tinycore.colonybridge.item.TabletEnergy;
import org.tinycore.colonybridge.item.TabletLink;
import org.tinycore.colonybridge.logic.tablet.TabletTabs;
import org.tinycore.colonybridge.menu.tablet.TabletView;
import org.tinycore.colonybridge.registry.ModItems;

import java.util.Objects;

/**
 * Tela de bloco aberta pelo tablet. Continua aberta enquanto (conferido pelo servidor a cada tick e a cada pacote):
 * <ul>
 *   <li>o jogador segura, na mesma mão, um tablet ligado a esta colônia;</li>
 *   <li>o bloco continua no mundo e o chunk dele está carregado (nunca carregamos chunk para isso);</li>
 *   <li>o jogador tem permissão para configurar o bloco (conferido a cada {@link #CHECK_TICKS});</li>
 *   <li>há bateria: a cada {@link #CHECK_TICKS} ticks o tablet gasta {@code tabletUsePerTick} × esse intervalo.</li>
 * </ul>
 * Quando algo falha, o jogador recebe o motivo na barra de ação e o Minecraft fecha a tela sozinho.
 * <p>
 * A bateria é gasta aqui (e não num tick próprio) porque o servidor chama {@code stillValid} todo tick com a tela
 * aberta: assim não há como abrir uma tela pelo tablet e esquecer de cobrar. O gasto é por tempo de jogo, então
 * pacotes extras (que também chamam {@code stillValid}) não gastam mais.
 */
public final class TabletAccess implements MenuAccess {

    /** Intervalo (ticks) entre gastos de bateria e checagens de permissão. */
    private static final int CHECK_TICKS = 20;

    private final AbstractBridgeBlockEntity blockEntity;
    private final Level level;
    private final InteractionHand hand;
    private final String colony;
    private final TabletView view;
    private long nextCheck = -1;
    private boolean warned;

    public TabletAccess(AbstractBridgeBlockEntity blockEntity, InteractionHand hand, String colony, TabletView view) {
        this.blockEntity = blockEntity;
        this.level = Objects.requireNonNull(blockEntity.getLevel());
        this.hand = hand;
        this.colony = colony;
        this.view = view;
    }

    @Override
    public boolean stillValid(Player player) {
        String problem = problem(player);
        if (problem != null && !warned) {
            warned = true;
            player.displayClientMessage(Component.translatable(problem).withStyle(ChatFormatting.RED), true);
        }
        return problem == null;
    }

    /** Chave de tradução do motivo para fechar a tela, ou null se tudo certo. */
    private @Nullable String problem(Player player) {
        ItemStack tablet = player.getItemInHand(hand);
        TabletLink link = ColonyTabletItem.link(tablet);
        if (!tablet.is(ModItems.COLONY_TABLET.get()) || link == null || !colony.equals(link.colony())) {
            return "tablet.tccolonybridge.closed.no_tablet";
        }
        if (blockEntity.isRemoved() || !level.isLoaded(blockEntity.getBlockPos())
                || level.getBlockEntity(blockEntity.getBlockPos()) != blockEntity) {
            return "tablet.tccolonybridge.closed.block_gone";
        }
        long now = level.getGameTime();
        if (now < nextCheck) {
            return null;
        }
        nextCheck = now + CHECK_TICKS;
        if (!blockEntity.canConfigure(player)) {
            return "link.tccolonybridge.no_permission";
        }
        int cost = TabletTabs.drainStep(Config.TABLET_USE_PER_TICK.get(), CHECK_TICKS);
        if (cost > 0 && TabletEnergy.storage(tablet).extractEnergy(cost, false) < cost) {
            return "tablet.tccolonybridge.closed.no_power";
        }
        return null;
    }

    @Override
    public ContainerLevelAccess levelAccess() {
        return ContainerLevelAccess.create(level, blockEntity.getBlockPos());
    }

    @Override
    public TabletView tabletView() {
        return view;
    }

    public InteractionHand hand() {
        return hand;
    }
}
