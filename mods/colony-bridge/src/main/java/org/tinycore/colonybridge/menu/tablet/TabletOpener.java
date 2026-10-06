package org.tinycore.colonybridge.menu.tablet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.AbstractBridgeBlock;
import org.tinycore.colonybridge.block.AbstractBridgeBlockEntity;
import org.tinycore.colonybridge.block.monitor.MonitorSource;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.item.ColonyTabletItem;
import org.tinycore.colonybridge.item.TabletEnergy;
import org.tinycore.colonybridge.item.TabletLink;
import org.tinycore.colonybridge.logic.colony.ColonyBlockRegistry;
import org.tinycore.colonybridge.logic.colony.ColonyBlockType;
import org.tinycore.colonybridge.logic.tablet.TabletTab;
import org.tinycore.colonybridge.logic.tablet.TabletTabs;
import org.tinycore.colonybridge.menu.access.TabletAccess;
import org.tinycore.colonybridge.registry.ModItems;

import java.util.EnumMap;
import java.util.Map;

/**
 * Servidor: abre a tela de um bloco da colônia pelo tablet (clique direito com o tablet ou troca de aba).
 * <ol>
 *   <li>confere o tablet na mão: ligado a uma colônia e com bateria (se a config cobra energia);</li>
 *   <li>acha os blocos da colônia pelo registro da Fase 8 ({@link ColonyBlockRegistry}) — só os de chunk
 *       <b>já carregado</b>, em qualquer dimensão;</li>
 *   <li>escolhe a aba ({@link TabletTabs#choose}) e confere a permissão do jogador no bloco;</li>
 *   <li>abre a mesma tela do bloco com o acesso do tablet ({@link TabletAccess}), que a mantém válida.</li>
 * </ol>
 * Nada vem do cliente além do número da aba, que só vale se estiver disponível.
 */
public final class TabletOpener {

    /** Tipo de bloco por trás de cada aba (tela do bloco ou painel dos dados dele). */
    private static final Map<TabletTab, ColonyBlockType> BLOCK_TABS = new EnumMap<>(Map.of(
            TabletTab.TERMINAL, ColonyBlockType.TERMINAL,
            TabletTab.BRIDGE, ColonyBlockType.BRIDGE,
            TabletTab.SUPPLY, ColonyBlockType.SUPPLY,
            TabletTab.BRIDGE_PANEL, ColonyBlockType.BRIDGE,
            TabletTab.SUPPLY_PANEL, ColonyBlockType.SUPPLY,
            TabletTab.CHUNK_LOADER, ColonyBlockType.CHUNK_LOADER,
            TabletTab.PATTERN_ENCODER, ColonyBlockType.PATTERN_ENCODER));

    private TabletOpener() {}

    /**
     * @param wanted aba pedida (troca de aba), ou null para a principal (clique direito)
     */
    public static void open(ServerPlayer player, InteractionHand hand, @Nullable TabletTab wanted) {
        ItemStack tablet = player.getItemInHand(hand);
        TabletLink link = ColonyTabletItem.link(tablet);
        if (!tablet.is(ModItems.COLONY_TABLET.get()) || link == null) {
            fail(player, "tablet.tccolonybridge.not_linked");
            return;
        }
        if (Config.TABLET_USE_PER_TICK.get() > 0 && TabletEnergy.stored(tablet) <= 0) {
            fail(player, "tablet.tccolonybridge.closed.no_power");
            return;
        }
        Map<TabletTab, AbstractBridgeBlockEntity> blocks = findBlocks(player, link.colony());
        int available = 0;
        for (TabletTab tab : blocks.keySet()) {
            available = TabletTabs.with(available, tab, true);
        }
        TabletTab tab = TabletTabs.choose(available, wanted);
        if (tab == null) {
            fail(player, wanted == null ? "tablet.tccolonybridge.nothing" : "tablet.tccolonybridge.unavailable");
            return;
        }
        AbstractBridgeBlockEntity be = blocks.get(tab);
        if (!be.canConfigure(player)) {
            fail(player, "link.tccolonybridge.no_permission");
            return;
        }
        TabletAccess access = new TabletAccess(be, hand, link.colony(), new TabletView(available, tab));
        if ((tab == TabletTab.BRIDGE_PANEL || tab == TabletTab.SUPPLY_PANEL) && be instanceof MonitorSource source) {
            openPanel(player, tab, source, access);
        } else if (be.getBlockState().getBlock() instanceof AbstractBridgeBlock<?> block) {
            block.openRemote(player, be, access);
        }
    }

    /** Aba de painel: menu próprio sem slots, com os dados do monitor do bloco. */
    private static void openPanel(ServerPlayer player, TabletTab tab, MonitorSource source, TabletAccess access) {
        Component title = Component.translatable("gui.tccolonybridge.tablet.tab." + tab.name().toLowerCase());
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new TabletPanelMenu(containerId, inventory, source, access), title),
                buf -> TabletView.write(buf, access.tabletView()));
    }

    /** Mão que segura um tablet (a principal primeiro), ou null. */
    public static @Nullable InteractionHand handWithTablet(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).is(ModItems.COLONY_TABLET.get())) {
                return hand;
            }
        }
        return null;
    }

    /** Blocos da colônia disponíveis agora, por aba (sem carregar chunk). */
    private static Map<TabletTab, AbstractBridgeBlockEntity> findBlocks(ServerPlayer player, String colony) {
        Map<TabletTab, AbstractBridgeBlockEntity> found = new EnumMap<>(TabletTab.class);
        ResourceLocation dimension = ColonyAccess.dimensionOf(colony);
        ServerLevel level = dimension == null ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return found;
        }
        ColonyBlockRegistry registry = ColonyBlockRegistry.get(level);
        BLOCK_TABS.forEach((tab, type) -> {
            BlockPos pos = registry.holder(colony, type);
            if (pos != null && level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof AbstractBridgeBlockEntity be
                    && !be.isRemoved() && be.colonyBlockType() == type) {
                found.put(tab, be);
            }
        });
        return found;
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
