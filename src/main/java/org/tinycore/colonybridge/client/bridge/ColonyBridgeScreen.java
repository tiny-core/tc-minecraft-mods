package org.tinycore.colonybridge.client.bridge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.block.bridge.BridgeSettings;
import org.tinycore.colonybridge.block.bridge.ItemFilter;
import org.tinycore.colonybridge.client.ui.FlatButton;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.logic.bridge.RequestOutcome;
import org.tinycore.colonybridge.menu.bridge.BridgeSnapshot;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.network.BridgeSettingsPayload;

import java.util.List;

/**
 * Tela da ponte (só cliente), com duas abas:
 * <ul>
 *   <li><b>Pedidos:</b> lista de pedidos com o resultado ({@link RequestListView}), crafting on/off e redstone;</li>
 *   <li><b>Filtro:</b> modo do filtro, tipo de comparação, 18 ghost slots e o inventário do jogador;</li>
 *   <li><b>Estatísticas:</b> totais, gráfico por hora e itens mais entregues ({@link StatsView}).</li>
 * </ul>
 * Não tem textura: tudo é desenhado com retângulos e texto usando {@link UiColors}.
 * Os dados vêm do {@link BridgeSnapshot} guardado no menu; os botões mandam um
 * {@link BridgeSettingsPayload} e o servidor decide se aplica.
 */
public class ColonyBridgeScreen extends AbstractContainerScreen<ColonyBridgeMenu> {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 230;
    private static final int PADDING = 8;
    private static final int LIST_TOP = 76;

    private static final int STATS_TOP = 54;

    /** Abas da tela. Só a de filtro mostra slots (o menu é avisado em {@link #selectTab}). */
    private enum Tab { REQUESTS, FILTER, STATS }

    /** Criadas no init(): a fonte da tela só existe depois dele. */
    private RequestListView requestList;
    private StatsView statsView;
    private Tab tab = Tab.REQUESTS;
    private FlatButton requestsTab;
    private FlatButton filterTab;
    private FlatButton statsTab;
    private FlatButton craftingButton;
    private FlatButton redstoneButton;
    private FlatButton filterModeButton;
    private FlatButton exactMatchButton;

    /** Ajustes locais: mudam na hora do clique e são corrigidos pelo próximo snapshot. */
    private BridgeSettings settings = BridgeSettings.DEFAULT;
    private BridgeSnapshot lastSeen;

    public ColonyBridgeScreen(ColonyBridgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        if (requestList == null) {
            requestList = new RequestListView(font);
            statsView = new StatsView(font);
        }
        int half = (WIDTH - PADDING * 3) / 2;
        int left = leftPos + PADDING;
        int right = leftPos + PADDING * 2 + half;
        requestsTab = addRenderableWidget(new FlatButton(left, topPos + 34, 70, 14,
                Component.translatable("gui.tccolonybridge.tab.requests"), () -> selectTab(Tab.REQUESTS)));
        filterTab = addRenderableWidget(new FlatButton(left + 74, topPos + 34, 70, 14,
                Component.translatable("gui.tccolonybridge.tab.filter"), () -> selectTab(Tab.FILTER)));
        statsTab = addRenderableWidget(new FlatButton(left + 148, topPos + 34, 70, 14,
                Component.translatable("gui.tccolonybridge.tab.stats"), () -> selectTab(Tab.STATS)));

        craftingButton = addRenderableWidget(new FlatButton(left, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withCrafting(!settings.craftingEnabled()))));
        redstoneButton = addRenderableWidget(new FlatButton(right, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withRedstone(settings.redstoneMode().next()))));
        filterModeButton = addRenderableWidget(new FlatButton(left, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withFilterMode(settings.filterMode().next()))));
        exactMatchButton = addRenderableWidget(new FlatButton(right, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withExactMatch(!settings.exactMatch()))));

        lastSeen = null; // força copiar o snapshot atual para os botões
        selectTab(tab); // init() roda de novo ao redimensionar a janela: mantém a aba atual
    }

    private void selectTab(Tab selected) {
        tab = selected;
        menu.setFilterTabOpen(selected == Tab.FILTER);
        requestsTab.setSelected(selected == Tab.REQUESTS);
        filterTab.setSelected(selected == Tab.FILTER);
        statsTab.setSelected(selected == Tab.STATS);
        craftingButton.visible = selected == Tab.REQUESTS;
        redstoneButton.visible = selected == Tab.REQUESTS;
        filterModeButton.visible = selected == Tab.FILTER;
        exactMatchButton.visible = selected == Tab.FILTER;
    }

    /** Chamado a cada tick do cliente: aplica um snapshot novo e atualiza os textos dos botões. */
    @Override
    protected void containerTick() {
        super.containerTick();
        BridgeSnapshot snapshot = menu.getSnapshot();
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            settings = snapshot.settings();
        }
        craftingButton.setMessage(Component.translatable("gui.tccolonybridge.crafting", onOff(settings.craftingEnabled())));
        redstoneButton.setMessage(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(settings.redstoneMode().translationKey())));
        filterModeButton.setMessage(Component.translatable("gui.tccolonybridge.filter_mode",
                Component.translatable(settings.filterMode().translationKey())));
        exactMatchButton.setMessage(Component.translatable(settings.exactMatch()
                ? "gui.tccolonybridge.match.exact" : "gui.tccolonybridge.match.item"));
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "gui.tccolonybridge.on" : "gui.tccolonybridge.off");
    }

    private void send(BridgeSettings newSettings) {
        settings = newSettings;
        PacketDistributor.sendToServer(new BridgeSettingsPayload(menu.containerId, newSettings));
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        BridgeSnapshot snap = menu.getSnapshot();
        int x = leftPos;
        int y = topPos;
        g.fill(x, y, x + WIDTH, y + HEIGHT, UiColors.BACKGROUND);
        g.fill(x, y, x + WIDTH, y + 2, UiColors.ACCENT);

        g.drawString(font, title, x + PADDING, y + 8, UiColors.ACCENT, false);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony")
                : Component.literal(snap.colonyName());
        g.drawString(font, colony, x + PADDING, y + 20, UiColors.HIGHLIGHT, false);

        Component status = Component.translatable(snap.status().guiKey());
        int statusX = x + WIDTH - PADDING - font.width(status);
        g.fill(statusX - 9, y + 22, statusX - 4, y + 27, StatusColors.of(snap.status()));
        g.drawString(font, status, statusX, y + 20, UiColors.TEXT, false);

        switch (tab) {
            case FILTER -> renderFilterTab(g, x, y);
            case STATS -> statsView.render(g, snap.stats(), x + PADDING, y + STATS_TOP, WIDTH - PADDING * 2);
            case REQUESTS -> {
                requestList.render(g, snap.lines(), x + PADDING, y + LIST_TOP, WIDTH - PADDING * 2, mouseX, mouseY);
                renderRequestsFooter(g, snap, x, y + HEIGHT - 14);
            }
        }
    }

    /** Fundo dos slots (os itens são desenhados pelo próprio AbstractContainerScreen). */
    private void renderFilterTab(GuiGraphics g, int x, int y) {
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            boolean ghost = slot.index < ItemFilter.SIZE; // index = posição no menu (0..17 = filtro)
            g.fill(sx, sy, sx + 18, sy + 18, ghost ? UiColors.ACCENT : UiColors.BORDER);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, UiColors.PANEL);
        }
        g.drawString(font, Component.translatable("gui.tccolonybridge.filter_hint"),
                x + PADDING, y + HEIGHT - 14, UiColors.TEXT_MUTED, false);
    }

    private void renderRequestsFooter(GuiGraphics g, BridgeSnapshot snap, int x, int y) {
        long crafting = snap.lines().stream()
                .filter(l -> l.outcome() == RequestOutcome.CRAFTING || l.outcome() == RequestOutcome.CRAFT_STARTED)
                .count();
        g.drawString(font, Component.translatable("gui.tccolonybridge.requests", snap.totalRequests()),
                x + PADDING, y, UiColors.TEXT_MUTED, false);
        Component craftText = Component.translatable("gui.tccolonybridge.crafting_count", crafting);
        g.drawString(font, craftText, x + WIDTH - PADDING - font.width(craftText), y, UiColors.TEXT_MUTED, false);
    }

    /** Título e inventário já são desenhados em renderBg; aqui não desenha nada. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (tab == Tab.REQUESTS) {
            requestList.renderTooltip(g, menu.getSnapshot().lines(), mouseX, mouseY);
        } else if (tab == Tab.STATS) {
            statsView.renderTooltip(g, menu.getSnapshot().stats().top(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.REQUESTS) {
            requestList.scroll(scrollY, menu.getSnapshot().lines().size());
        }
        return true;
    }

    /** Ghost slots visíveis agora (usado pela integração com JEI para saber onde soltar itens). */
    public List<Slot> visibleFilterSlots() {
        return menu.isFilterTabOpen() ? menu.slots.subList(0, ItemFilter.SIZE) : List.of();
    }
}
