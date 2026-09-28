package org.tinycore.colonybridge.client.bridge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.BridgeSettings;
import org.tinycore.colonybridge.block.bridge.CraftSettings;
import org.tinycore.colonybridge.client.ui.FlatButton;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.logic.bridge.RequestCounts;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;
import org.tinycore.colonybridge.menu.bridge.BridgeSnapshot;
import org.tinycore.colonybridge.menu.bridge.BridgeTab;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.network.BridgeSettingsPayload;
import org.tinycore.colonybridge.network.BridgeTabPayload;
import org.tinycore.colonybridge.network.CraftSettingsPayload;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tela da ponte (só cliente), com quatro abas ({@link BridgeTab}):
 * <ul>
 *   <li><b>Geral:</b> resumo dos pedidos, crafting on/off, redstone e preferência de craft por tag;</li>
 *   <li><b>Filtro:</b> modo do filtro, tipo de comparação, 18 ghost slots e o inventário;</li>
 *   <li><b>Preferidos:</b> 9 ghost slots com a ordem de preferência do modo "Lista";</li>
 *   <li><b>Mods:</b> modo de mods e a lista de mods craftáveis da rede ({@link ModListView}).</li>
 * </ul>
 * A lista de pedidos e as estatísticas ficam nos monitores; aqui só o resumo.
 * Não tem textura: tudo é desenhado com retângulos e texto usando {@link UiColors}. Os dados vêm do
 * {@link BridgeSnapshot} guardado no menu; os botões mandam pacotes e o servidor decide se aplica.
 */
public class ColonyBridgeScreen extends AbstractContainerScreen<ColonyBridgeMenu> {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 230;
    private static final int PADDING = 8;
    private static final int TAB_Y = 34;
    private static final int ROW1_Y = 54;
    private static final int ROW2_Y = 74;
    private static final int MOD_LIST_Y = 76;
    private static final int MOD_LIST_HEIGHT = 132;

    /** Criada no init(): a fonte da tela só existe depois dele. */
    private ModListView modList;
    private BridgeTab tab = BridgeTab.GENERAL;
    private final FlatButton[] tabButtons = new FlatButton[BridgeTab.values().length];
    private FlatButton craftingButton;
    private FlatButton redstoneButton;
    private FlatButton preferenceButton;
    private FlatButton filterModeButton;
    private FlatButton exactMatchButton;
    private FlatButton modModeButton;

    /** Ajustes locais: mudam na hora do clique e são corrigidos pelo próximo snapshot. */
    private BridgeSettings settings = BridgeSettings.DEFAULT;
    private CraftSettings craft = CraftSettings.DEFAULT;
    private Set<String> markedMods = Set.of();
    private BridgeSnapshot lastSeen;

    public ColonyBridgeScreen(ColonyBridgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        if (modList == null) {
            modList = new ModListView(font);
        }
        int inner = WIDTH - PADDING * 2;
        int half = (inner - PADDING) / 2;
        int left = leftPos + PADDING;
        int right = left + half + PADDING;
        int tabWidth = (inner - 4 * (tabButtons.length - 1)) / tabButtons.length;
        for (BridgeTab t : BridgeTab.values()) {
            tabButtons[t.ordinal()] = addRenderableWidget(new FlatButton(left + t.ordinal() * (tabWidth + 4),
                    topPos + TAB_Y, tabWidth, 14,
                    Component.translatable("gui.tccolonybridge.tab." + t.name().toLowerCase()), () -> selectTab(t)));
        }

        craftingButton = addRenderableWidget(new FlatButton(left, topPos + ROW1_Y, half, 16, Component.empty(),
                () -> send(settings.withCrafting(!settings.craftingEnabled()))));
        redstoneButton = addRenderableWidget(new FlatButton(right, topPos + ROW1_Y, half, 16, Component.empty(),
                () -> send(settings.withRedstone(settings.redstoneMode().next()))));
        preferenceButton = addRenderableWidget(new FlatButton(left, topPos + ROW2_Y, inner, 16, Component.empty(),
                () -> send(craft.withPreference(craft.nextPreference()))));
        filterModeButton = addRenderableWidget(new FlatButton(left, topPos + ROW1_Y, half, 16, Component.empty(),
                () -> send(settings.withFilterMode(settings.filterMode().next()))));
        exactMatchButton = addRenderableWidget(new FlatButton(right, topPos + ROW1_Y, half, 16, Component.empty(),
                () -> send(settings.withExactMatch(!settings.exactMatch()))));
        modModeButton = addRenderableWidget(new FlatButton(left, topPos + ROW1_Y, inner, 16, Component.empty(),
                () -> send(craft.withModMode(craft.modMode().next()))));

        lastSeen = null; // força copiar o snapshot atual para os botões
        selectTab(tab); // init() roda de novo ao redimensionar a janela: mantém a aba atual
    }

    private void selectTab(BridgeTab selected) {
        tab = selected;
        menu.setTab(selected);
        PacketDistributor.sendToServer(new BridgeTabPayload(menu.containerId, selected.ordinal()));
        for (BridgeTab t : BridgeTab.values()) {
            tabButtons[t.ordinal()].setSelected(t == selected);
        }
        craftingButton.visible = selected == BridgeTab.GENERAL;
        redstoneButton.visible = selected == BridgeTab.GENERAL;
        preferenceButton.visible = selected == BridgeTab.GENERAL;
        filterModeButton.visible = selected == BridgeTab.FILTER;
        exactMatchButton.visible = selected == BridgeTab.FILTER;
        modModeButton.visible = selected == BridgeTab.MODS;
    }

    /** Chamado a cada tick do cliente: aplica um snapshot novo e atualiza os textos dos botões. */
    @Override
    protected void containerTick() {
        super.containerTick();
        BridgeSnapshot snapshot = menu.getSnapshot();
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            settings = snapshot.settings();
            setCraft(snapshot.craftSettings());
        }
        craftingButton.setMessage(Component.translatable("gui.tccolonybridge.crafting", onOff(settings.craftingEnabled())));
        redstoneButton.setMessage(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(settings.redstoneMode().translationKey())));
        preferenceButton.setMessage(Component.translatable("gui.tccolonybridge.preference",
                preferenceName(craft.preference())));
        filterModeButton.setMessage(Component.translatable("gui.tccolonybridge.filter_mode",
                Component.translatable(settings.filterMode().translationKey())));
        exactMatchButton.setMessage(Component.translatable(settings.exactMatch()
                ? "gui.tccolonybridge.match.exact" : "gui.tccolonybridge.match.item"));
        modModeButton.setMessage(Component.translatable("gui.tccolonybridge.mod_mode",
                Component.translatable(craft.modMode().translationKey())));
    }

    private void setCraft(CraftSettings value) {
        craft = value;
        markedMods = new HashSet<>(value.mods());
        modList.setMods(menu.getSnapshot().craftableMods(), value.mods());
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "gui.tccolonybridge.on" : "gui.tccolonybridge.off");
    }

    /** "Padrão do servidor" ou o nome da preferência (mesmas chaves da tela de config do NeoForge). */
    private static Component preferenceName(@Nullable CraftPreference preference) {
        return preference == null
                ? Component.translatable("gui.tccolonybridge.preference.server")
                : preference.getTranslatedName();
    }

    private void send(BridgeSettings newSettings) {
        settings = newSettings;
        PacketDistributor.sendToServer(new BridgeSettingsPayload(menu.containerId, newSettings));
    }

    private void send(CraftSettings newSettings) {
        setCraft(newSettings);
        PacketDistributor.sendToServer(new CraftSettingsPayload(menu.containerId, newSettings));
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
            case GENERAL -> renderGeneral(g, snap.counts(), x + PADDING, y + ROW2_Y + 26);
            case FILTER -> renderSlots(g, x, y, "gui.tccolonybridge.filter_hint");
            case PREFERRED -> {
                g.drawString(font, Component.translatable("gui.tccolonybridge.preferred.info"),
                        x + PADDING, y + ROW1_Y + 4, UiColors.TEXT, false);
                g.drawString(font, Component.translatable("gui.tccolonybridge.preferred.fallback"),
                        x + PADDING, y + ROW1_Y + 14, UiColors.TEXT_MUTED, false);
                renderSlots(g, x, y, "gui.tccolonybridge.filter_hint");
            }
            case MODS -> {
                modList.render(g, markedMods, x + PADDING, y + MOD_LIST_Y, WIDTH - PADDING * 2, MOD_LIST_HEIGHT,
                        mouseX, mouseY);
                g.drawString(font, Component.translatable("gui.tccolonybridge.mods.hint"),
                        x + PADDING, y + HEIGHT - 12, UiColors.TEXT_MUTED, false);
            }
        }
    }

    /** Resumo do último ciclo: cartões com total, atendidos, craftando e pendentes. */
    private void renderGeneral(GuiGraphics g, RequestCounts counts, int x, int y) {
        int inner = WIDTH - PADDING * 2;
        int cardWidth = (inner - 3 * 4) / 4;
        card(g, x, y, cardWidth, "gui.tccolonybridge.count.total", counts.total(), UiColors.TEXT);
        card(g, x + (cardWidth + 4), y, cardWidth, "gui.tccolonybridge.count.served", counts.served(), UiColors.SUCCESS);
        card(g, x + (cardWidth + 4) * 2, y, cardWidth, "gui.tccolonybridge.count.crafting", counts.crafting(),
                UiColors.HIGHLIGHT);
        card(g, x + (cardWidth + 4) * 3, y, cardWidth, "gui.tccolonybridge.count.pending", counts.pending(),
                counts.pending() > 0 ? UiColors.WARNING : UiColors.TEXT_MUTED);
        g.drawWordWrap(font, Component.translatable("gui.tccolonybridge.monitor_hint"), x, y + 44, inner,
                UiColors.TEXT_MUTED);
    }

    private void card(GuiGraphics g, int x, int y, int width, String labelKey, int value, int color) {
        g.fill(x, y, x + width, y + 34, UiColors.PANEL);
        g.drawString(font, Component.translatable(labelKey), x + 4, y + 4, UiColors.TEXT_MUTED, false);
        g.drawString(font, String.valueOf(value), x + 4, y + 18, color, false);
    }

    /** Fundo dos slots visíveis (os itens são desenhados pelo próprio AbstractContainerScreen). */
    private void renderSlots(GuiGraphics g, int x, int y, String hintKey) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            boolean ghost = slot.index < ColonyBridgeMenu.GHOST_COUNT;
            g.fill(sx, sy, sx + 18, sy + 18, ghost ? UiColors.ACCENT : UiColors.BORDER);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, UiColors.PANEL);
        }
        g.drawString(font, Component.translatable(hintKey), x + PADDING, y + HEIGHT - 14, UiColors.TEXT_MUTED, false);
    }

    /** Título e inventário já são desenhados em renderBg; aqui não desenha nada. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == BridgeTab.MODS && button == 0) {
            String mod = modList.modAt(mouseX, mouseY, leftPos + PADDING, topPos + MOD_LIST_Y,
                    WIDTH - PADDING * 2, MOD_LIST_HEIGHT);
            if (mod != null) {
                send(craft.toggleMod(mod));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == BridgeTab.MODS) {
            modList.scroll(scrollY, MOD_LIST_HEIGHT);
        }
        return true;
    }

    /** Ghost slots visíveis agora (usado pela integração com JEI para saber onde soltar itens). */
    public List<Slot> visibleGhostSlots() {
        return menu.slots.subList(0, ColonyBridgeMenu.GHOST_COUNT).stream().filter(Slot::isActive).toList();
    }
}
