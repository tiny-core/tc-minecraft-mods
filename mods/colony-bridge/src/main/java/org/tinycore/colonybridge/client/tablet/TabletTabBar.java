package org.tinycore.colonybridge.client.tablet;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.tablet.TabletTab;
import org.tinycore.colonybridge.menu.tablet.TabletView;
import org.tinycore.colonybridge.network.TabletOpenPayload;
import org.tinycore.colonybridge.registry.ModItems;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.ScreenStyle;

import java.util.function.UnaryOperator;

/**
 * Barra de abas do tablet, acima da janela das telas abertas por ele (Terminal, Ponte, Abastecedor). Cada aba é
 * um botão com o ícone do bloco; a aberta fica marcada e as indisponíveis (bloco ausente ou em chunk
 * descarregado) ficam desativadas, com o motivo na dica. Clicar numa aba pede ao servidor para abrir a tela
 * daquele bloco ({@link TabletOpenPayload}); o servidor decide.
 * <p>
 * Abas de etapas ainda não feitas ({@link TabletTab#implemented}) não aparecem.
 */
public final class TabletTabBar {

    /** Altura reservada acima da janela. */
    public static final int HEIGHT = IconButton.SIZE + 4;

    private TabletTabBar() {}

    /** Pixels a descer a janela para a barra caber acima dela (0 sem tablet). */
    public static int offset(@Nullable TabletView view) {
        return view == null ? 0 : HEIGHT / 2;
    }

    /**
     * Cria os botões das abas (nada, se a tela não veio do tablet).
     *
     * @param add registra o botão na tela ({@code addRenderableWidget})
     * @param top topo da janela (a barra fica logo acima)
     */
    public static void add(@Nullable TabletView view, UnaryOperator<IconButton> add, int left, int top) {
        if (view == null) {
            return;
        }
        int x = left;
        for (TabletTab tab : TabletTab.values()) {
            if (!tab.implemented()) {
                continue;
            }
            boolean available = view.isAvailable(tab);
            IconButton button = new IconButton(x, top - HEIGHT, () -> open(view, tab)).icon(icon(tab));
            button.setSelected(tab == view.active());
            button.active = available;
            if (!available) {
                button.setBadge(ScreenStyle.DANGER);
            }
            Component name = Component.translatable("gui.tccolonybridge.tablet.tab." + tab.name().toLowerCase());
            button.setTooltipText(available ? name
                    : Component.translatable("gui.tccolonybridge.tablet.tab_unavailable", name));
            add.apply(button);
            x += IconButton.SIZE + 2;
        }
    }

    private static void open(TabletView view, TabletTab tab) {
        if (tab != view.active()) {
            PacketDistributor.sendToServer(new TabletOpenPayload(tab.ordinal()));
        }
    }

    private static ItemStack icon(TabletTab tab) {
        return switch (tab) {
            case TERMINAL -> new ItemStack(ModItems.WAREHOUSE_TERMINAL.get());
            case BRIDGE -> new ItemStack(ModItems.COLONY_BRIDGE.get());
            case SUPPLY -> new ItemStack(ModItems.COLONY_SUPPLY.get());
            case BRIDGE_PANEL, SUPPLY_PANEL -> new ItemStack(ModItems.COLONY_MONITOR.get());
            case CHUNK_LOADER -> new ItemStack(ModItems.CHUNK_LOADER.get());
            case PATTERN_ENCODER -> new ItemStack(ModItems.PATTERN_ENCODER.get());
        };
    }
}
