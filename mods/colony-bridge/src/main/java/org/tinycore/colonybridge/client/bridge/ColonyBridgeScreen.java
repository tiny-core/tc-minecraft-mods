package org.tinycore.colonybridge.client.bridge;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.BridgeSettings;
import org.tinycore.colonybridge.block.bridge.CraftSettings;
import org.tinycore.colonybridge.client.list.TargetListWidget;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.logic.bridge.RequestCounts;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.bridge.BridgeSnapshot;
import org.tinycore.colonybridge.menu.bridge.BridgeTab;
import org.tinycore.colonybridge.menu.bridge.ColonyBridgeMenu;
import org.tinycore.colonybridge.network.BridgeSettingsPayload;
import org.tinycore.colonybridge.network.BridgeTabPayload;
import org.tinycore.colonybridge.network.CraftSettingsPayload;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.RedstoneIcons;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tela da ponte (só cliente), com a estrutura dos terminais do AE2 e as cores da marca ({@link ScreenStyle}):
 * <ul>
 *   <li><b>barras laterais</b> ({@link SideToolbar}) com botões de ícone: à esquerda o que vale para a
 *       ponte toda (ajuda "?", crafting, redstone); à direita os ajustes da aba aberta; o valor atual
 *       aparece no tooltip;</li>
 *   <li><b>abas com ícone</b> ({@link BridgeTab}): Geral (resumo), Filtro, Preferidos e Mods;</li>
 *   <li>cada aba tem uma <b>seção com título</b>; Filtro e Preferidos mostram o inventário embaixo;</li>
 *   <li>o <b>filtro</b> é uma lista de linhas ({@link TargetListWidget}: item, {@code #tag} ou {@code @mod}); os
 *       preferidos continuam em grade de ghost slots.</li>
 * </ul>
 * A lista de pedidos e as estatísticas ficam nos monitores. Os dados vêm do {@link BridgeSnapshot} guardado
 * no menu; os botões mandam pacotes e o servidor decide se aplica.
 */
public class ColonyBridgeScreen extends AbstractContainerScreen<ColonyBridgeMenu> {

    private static final int WIDTH = 202;
    private static final int HEIGHT = 263;
    private static final int PADDING = 8;
    private static final int TABS_Y = 31;
    private static final int SECTION_Y = 53;
    private static final int CONTENT_Y = 64;
    private static final int MOD_LIST_HEIGHT = 120;
    private static final int FILTER_LIST_Y = 66;
    private static final int FILTER_ROWS = 5;

    /** Criada no init(): a fonte da tela só existe depois dele. */
    private ModListView modList;
    private TargetListWidget filterList;
    private BridgeTab tab = BridgeTab.GENERAL;
    private final IconButton[] tabButtons = new IconButton[BridgeTab.values().length];
    /** Esquerda: ajustes do bloco todo. Direita: ajustes da aba aberta. */
    private SideToolbar toolbar;
    private SideToolbar tabToolbar;
    private IconButton helpButton;
    private IconButton craftingButton;
    private IconButton redstoneButton;
    private IconButton preferenceButton;
    private IconButton filterModeButton;
    private IconButton exactMatchButton;
    private IconButton modModeButton;

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
        for (BridgeTab t : BridgeTab.values()) {
            IconButton button = new IconButton(leftPos + PADDING + t.ordinal() * (IconButton.SIZE + 2),
                    topPos + TABS_Y, () -> selectTab(t)).icon(BridgeIcons.tab(t));
            button.setTooltipText(Component.translatable("gui.tccolonybridge.tab." + t.name().toLowerCase()));
            tabButtons[t.ordinal()] = addRenderableWidget(button);
        }

        filterList = new TargetListWidget(font, TargetListKind.FILTER, menu.containerId, menu.targetLists(), null,
                FILTER_ROWS);
        filterList.init(this::addRenderableWidget, this::setFocused, leftPos + PADDING, topPos + FILTER_LIST_Y,
                WIDTH - PADDING * 2);

        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        tabToolbar = new SideToolbar(SideToolbar.Side.RIGHT);
        helpButton = tool(toolbar, new IconButton(0, 0, () -> {}).glyph("?"));
        craftingButton = tool(toolbar,
                new IconButton(0, 0, () -> send(settings.withCrafting(!settings.craftingEnabled()))));
        redstoneButton = tool(toolbar,
                new IconButton(0, 0, () -> send(settings.withRedstone(settings.redstoneMode().next()))));
        preferenceButton = tool(tabToolbar,
                new IconButton(0, 0, () -> send(craft.withPreference(craft.nextPreference()))));
        filterModeButton = tool(tabToolbar,
                new IconButton(0, 0, () -> send(settings.withFilterMode(settings.filterMode().next()))));
        exactMatchButton = tool(tabToolbar,
                new IconButton(0, 0, () -> send(settings.withExactMatch(!settings.exactMatch()))));
        modModeButton = tool(tabToolbar, new IconButton(0, 0, () -> send(craft.withModMode(craft.modMode().next()))));

        lastSeen = null; // força copiar o snapshot atual para os botões
        selectTab(tab); // init() roda de novo ao redimensionar a janela: mantém a aba atual
    }

    private IconButton tool(SideToolbar bar, IconButton button) {
        return addRenderableWidget(bar.add(button));
    }

    private void selectTab(BridgeTab selected) {
        tab = selected;
        menu.setTab(selected);
        PacketDistributor.sendToServer(new BridgeTabPayload(menu.containerId, selected.ordinal()));
        for (BridgeTab t : BridgeTab.values()) {
            tabButtons[t.ordinal()].setSelected(t == selected);
        }
        preferenceButton.visible = selected == BridgeTab.GENERAL || selected == BridgeTab.PREFERRED;
        filterModeButton.visible = selected == BridgeTab.FILTER;
        exactMatchButton.visible = selected == BridgeTab.FILTER;
        modModeButton.visible = selected == BridgeTab.MODS;
        filterList.setVisible(selected == BridgeTab.FILTER);
        toolbar.layout(leftPos, topPos, WIDTH);
        tabToolbar.layout(leftPos, topPos, WIDTH);
        helpButton.setTooltipText(Component.translatable("gui.tccolonybridge.help." + selected.name().toLowerCase()));
    }

    /** Chamado a cada tick do cliente: aplica um snapshot novo e atualiza ícones e tooltips. */
    @Override
    protected void containerTick() {
        super.containerTick();
        BridgeSnapshot snapshot = menu.getSnapshot();
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            settings = snapshot.settings();
            setCraft(snapshot.craftSettings());
        }
        craftingButton.icon(BridgeIcons.crafting());
        craftingButton.setBadge(settings.craftingEnabled() ? ScreenStyle.SUCCESS : ScreenStyle.DANGER);
        craftingButton.setTooltipText(Component.translatable("gui.tccolonybridge.crafting",
                onOff(settings.craftingEnabled())));
        redstoneButton.icon(RedstoneIcons.of(settings.redstoneMode()));
        redstoneButton.setTooltipText(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(settings.redstoneMode().translationKey())));
        preferenceButton.icon(BridgeIcons.preference(craft.preference()));
        preferenceButton.setTooltipText(Component.translatable("gui.tccolonybridge.preference",
                preferenceName(craft.preference())));
        filterModeButton.icon(BridgeIcons.filterMode(settings.filterMode()));
        filterModeButton.setTooltipText(Component.translatable("gui.tccolonybridge.filter_mode",
                Component.translatable(settings.filterMode().translationKey())));
        exactMatchButton.icon(BridgeIcons.exactMatch(settings.exactMatch()));
        exactMatchButton.setTooltipText(Component.translatable(settings.exactMatch()
                ? "gui.tccolonybridge.match.exact" : "gui.tccolonybridge.match.item"));
        modModeButton.icon(BridgeIcons.modMode(craft.modMode()));
        modModeButton.setTooltipText(Component.translatable("gui.tccolonybridge.mod_mode",
                Component.translatable(craft.modMode().translationKey())));
        filterList.tick();
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
        int inner = WIDTH - PADDING * 2;
        toolbar.render(g);
        tabToolbar.render(g);
        ScreenStyle.window(g, x, y, WIDTH, HEIGHT);

        // Estado à direita primeiro: o título usa o espaço que sobrar (corta com "…" se preciso).
        Component status = Component.translatable(snap.status().guiKey());
        int statusWidth = ScreenStyle.drawFittedRight(g, font, status, x + WIDTH - PADDING, y + 7, inner / 2,
                ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + WIDTH - PADDING - statusWidth - 10, y + 7, StatusColors.of(snap.status()));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, y + 7, inner - statusWidth - 16, ScreenStyle.TITLE);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony")
                : Component.literal(snap.colonyName());
        ScreenStyle.drawFitted(g, font, colony, x + PADDING, y + 19, inner, ScreenStyle.INFO);

        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.section." + tab.name().toLowerCase()),
                x + PADDING, y + SECTION_Y, inner, ScreenStyle.TEXT);
        switch (tab) {
            case GENERAL -> renderGeneral(g, snap.counts(), x + PADDING, y + CONTENT_Y);
            case FILTER -> {
                filterList.render(g, mouseX, mouseY);
                renderSlots(g, x, y);
            }
            case PREFERRED -> renderSlots(g, x, y);
            case MODS -> {
                int listWidth = inner - 8;
                ScreenStyle.inset(g, x + PADDING, y + CONTENT_Y - 2, listWidth, MOD_LIST_HEIGHT + 4, ScreenStyle.SLOT);
                modList.render(g, markedMods, x + PADDING + 2, y + CONTENT_Y, listWidth - 4, MOD_LIST_HEIGHT,
                        mouseX, mouseY);
                ScreenStyle.scrollbar(g, x + WIDTH - PADDING - 6, y + CONTENT_Y - 2, MOD_LIST_HEIGHT + 4,
                        modList.firstRow(), modList.visibleRows(MOD_LIST_HEIGHT), modList.size());
            }
        }
    }

    /** Resumo do último ciclo: quatro cartões (2×2) e o aviso de que os detalhes estão no monitor. */
    private void renderGeneral(GuiGraphics g, RequestCounts counts, int x, int y) {
        int inner = WIDTH - PADDING * 2;
        int cardWidth = (inner - 4) / 2;
        card(g, x, y, cardWidth, "gui.tccolonybridge.count.total", counts.total(), ScreenStyle.TEXT);
        card(g, x + cardWidth + 4, y, cardWidth, "gui.tccolonybridge.count.served", counts.served(),
                ScreenStyle.SUCCESS);
        card(g, x, y + 32, cardWidth, "gui.tccolonybridge.count.crafting", counts.crafting(), ScreenStyle.INFO);
        card(g, x + cardWidth + 4, y + 32, cardWidth, "gui.tccolonybridge.count.pending", counts.pending(),
                counts.pending() > 0 ? ScreenStyle.WARNING : ScreenStyle.TEXT_MUTED);
        g.drawWordWrap(font, Component.translatable("gui.tccolonybridge.monitor_hint"), x, y + 70, inner,
                ScreenStyle.TEXT_MUTED);
    }

    private void card(GuiGraphics g, int x, int y, int width, String labelKey, int value, int color) {
        ScreenStyle.inset(g, x, y, width, 28, ScreenStyle.PANEL);
        ScreenStyle.drawFitted(g, font, Component.translatable(labelKey), x + 4, y + 4, width - 8,
                ScreenStyle.TEXT_MUTED);
        ScreenStyle.drawFitted(g, font, Component.literal(String.valueOf(value)), x + 4, y + 16, width - 8, color);
    }

    /** Fundo dos slots visíveis e o título "Inventário" (os itens são desenhados pelo próprio Minecraft). */
    private void renderSlots(GuiGraphics g, int x, int y) {
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
            }
        }
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.inventory"),
                x + ColonyBridgeMenu.FILTER_X - 1, y + ColonyBridgeMenu.INVENTORY_Y - 11, WIDTH - PADDING * 2,
                ScreenStyle.TEXT);
    }

    /** Título e inventário já são desenhados em renderBg; aqui não desenha nada. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tip = filterList.tooltip(mouseX, mouseY);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (filterList.mouseClicked(mouseX, mouseY, button, menu.getCarried())) {
            return true;
        }
        if (tab == BridgeTab.MODS && button == 0) {
            String mod = modList.modAt(mouseX, mouseY, leftPos + PADDING + 2, topPos + CONTENT_Y,
                    WIDTH - PADDING * 2 - 12, MOD_LIST_HEIGHT);
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
        filterList.mouseScrolled(mouseX, mouseY, scrollY, hasShiftDown(), hasControlDown());
        return true;
    }

    /** Com uma caixa de texto do filtro em foco, as teclas vão para ela (senão "E" fecharia a tela). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return filterList.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Lista do filtro, para o JEI saber onde soltar itens (vazia fora da aba "Filtro"). */
    public TargetListWidget listWidget() {
        return filterList;
    }

    /** Ghost slots visíveis agora (usado pela integração com JEI para saber onde soltar itens). */
    public List<Slot> visibleGhostSlots() {
        return menu.slots.subList(0, ColonyBridgeMenu.GHOST_COUNT).stream().filter(Slot::isActive).toList();
    }

    /** Áreas fora da janela ocupadas pela tela (as barras laterais), para o JEI não desenhar por cima. */
    public List<Rect2i> extraAreas() {
        return List.of(toolbar.area(), tabToolbar.area());
    }
}
