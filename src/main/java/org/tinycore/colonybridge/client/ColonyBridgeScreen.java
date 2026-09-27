package org.tinycore.colonybridge.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.block.BridgeSettings;
import org.tinycore.colonybridge.block.ItemFilter;
import org.tinycore.colonybridge.client.ui.FlatButton;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.RequestOutcome;
import org.tinycore.colonybridge.menu.BridgeSnapshot;
import org.tinycore.colonybridge.menu.ColonyBridgeMenu;
import org.tinycore.colonybridge.network.BridgeSettingsPayload;

import java.util.List;

/**
 * Tela da ponte (só cliente), com duas abas:
 * <ul>
 *   <li><b>Pedidos:</b> lista de pedidos com o resultado ({@link RequestListView}), crafting on/off e redstone;</li>
 *   <li><b>Filtro:</b> modo do filtro, tipo de comparação, 18 ghost slots e o inventário do jogador.</li>
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

    /** Criada no init(): a fonte da tela só existe depois dele. */
    private RequestListView requestList;
    private FlatButton requestsTab;
    private FlatButton filterTab;
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
        }
        int half = (WIDTH - PADDING * 3) / 2;
        int left = leftPos + PADDING;
        int right = leftPos + PADDING * 2 + half;
        requestsTab = addRenderableWidget(new FlatButton(left, topPos + 34, 70, 14,
                Component.translatable("gui.tccolonybridge.tab.requests"), () -> selectTab(false)));
        filterTab = addRenderableWidget(new FlatButton(left + 74, topPos + 34, 70, 14,
                Component.translatable("gui.tccolonybridge.tab.filter"), () -> selectTab(true)));

        craftingButton = addRenderableWidget(new FlatButton(left, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withCrafting(!settings.craftingEnabled()))));
        redstoneButton = addRenderableWidget(new FlatButton(right, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withRedstone(settings.redstoneMode().next()))));
        filterModeButton = addRenderableWidget(new FlatButton(left, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withFilterMode(settings.filterMode().next()))));
        exactMatchButton = addRenderableWidget(new FlatButton(right, topPos + 54, half, 16, Component.empty(),
                () -> send(settings.withExactMatch(!settings.exactMatch()))));

        lastSeen = null; // força copiar o snapshot atual para os botões
        selectTab(menu.isFilterTabOpen());
    }

    private void selectTab(boolean filter) {
        menu.setFilterTabOpen(filter);
        requestsTab.setSelected(!filter);
        filterTab.setSelected(filter);
        craftingButton.visible = !filter;
        redstoneButton.visible = !filter;
        filterModeButton.visible = filter;
        exactMatchButton.visible = filter;
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
        g.fill(statusX - 9, y + 22, statusX - 4, y + 27, statusColor(snap.status()));
        g.drawString(font, status, statusX, y + 20, UiColors.TEXT, false);

        if (menu.isFilterTabOpen()) {
            renderFilterTab(g, x, y);
        } else {
            requestList.render(g, snap.lines(), x + PADDING, y + LIST_TOP, WIDTH - PADDING * 2, mouseX, mouseY);
            renderRequestsFooter(g, snap, x, y + HEIGHT - 14);
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
        if (!menu.isFilterTabOpen()) {
            requestList.renderTooltip(g, menu.getSnapshot().lines(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!menu.isFilterTabOpen()) {
            requestList.scroll(scrollY, menu.getSnapshot().lines().size());
        }
        return true;
    }

    /** Ghost slots visíveis agora (usado pela integração com JEI para saber onde soltar itens). */
    public List<Slot> visibleFilterSlots() {
        return menu.isFilterTabOpen() ? menu.slots.subList(0, ItemFilter.SIZE) : List.of();
    }

    private static int statusColor(BridgeStatus status) {
        return switch (status) {
            case WORKING -> UiColors.HIGHLIGHT;
            case IDLE -> UiColors.SUCCESS;
            case STARTING, PAUSED -> UiColors.WARNING;
            case OFFLINE, INVALID_CABLE -> UiColors.TEXT_MUTED;
            case NO_COLONY, NO_PERMISSION, NO_WAREHOUSE -> UiColors.DANGER;
        };
    }
}
