package org.tinycore.colonybridge.client.supply;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.client.list.TargetListWidget;
import org.tinycore.colonybridge.client.tablet.TabletTabBar;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.menu.TargetLineView;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.menu.supply.SupplyLineStat;
import org.tinycore.colonybridge.menu.supply.SupplySnapshot;
import org.tinycore.colonybridge.network.SupplyConfigPayload;
import org.tinycore.colonybridge.network.TargetEditPayload;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.RedstoneIcons;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;
import org.tinycore.core.client.ui.TcIcons;

import java.util.List;

/**
 * Tela do Abastecedor (só cliente), com a estrutura dos terminais do AE2 e as cores da marca
 * ({@link ScreenStyle}): barra lateral com ajuda "?", redstone e auto-craft, duas abas (Manter no armazém / Excedente para
 * o ME), cada uma com a sua lista de linhas ({@link TargetListWidget}), e o inventário embaixo.
 * <p>
 * As listas são editadas pela própria {@link TargetListWidget} (pacotes ao servidor). Aqui ficam o layout, as
 * abas e o que só o Abastecedor tem: a cor de cada linha e a dica com "no armazém agora" ({@link Info}).
 */
public class ColonySupplyScreen extends AbstractContainerScreen<ColonySupplyMenu> {

    private static final int WIDTH = 202;
    private static final int HEIGHT = 261;
    private static final int PADDING = 8;
    private static final int TABS_Y = 31;
    private static final int TAB_HEIGHT = 14;
    private static final int LIST_Y = 62;
    private static final int LIST_ROWS = 5;

    private SideToolbar toolbar;
    private IconButton redstoneButton;
    private IconButton craftButton;
    private TargetListWidget keepList;
    private TargetListWidget surplusList;
    private boolean keepTab = true;
    /** Estado local: muda na hora do clique e é corrigido pelo próximo snapshot do servidor. */
    private SupplySnapshot settings = SupplySnapshot.EMPTY;
    private SupplySnapshot lastSeen;

    public ColonySupplyScreen(ColonySupplyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos += TabletTabBar.offset(menu.tabletView()); // abre espaço para as abas do tablet acima da janela
        TabletTabBar.add(menu.tabletView(), this::addRenderableWidget, leftPos, topPos);
        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        IconButton help = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> {}).sprite(TcIcons.HELP)));
        help.setTooltipText(Component.translatable("gui.tccolonybridge.help.supply"));
        redstoneButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, this::cycleRedstone)));
        craftButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, this::toggleCraft)
                .icon(new ItemStack(Items.CRAFTING_TABLE))));
        toolbar.layout(leftPos, topPos, WIDTH);

        keepList = list(TargetListKind.KEEP, true);
        surplusList = list(TargetListKind.SURPLUS, false);
        selectTab(keepTab);
        lastSeen = null; // força copiar o snapshot atual
    }

    private TargetListWidget list(TargetListKind kind, boolean keep) {
        TargetListWidget widget = new TargetListWidget(font, kind, menu.containerId, menu.targetLists(),
                new Info(keep), LIST_ROWS);
        widget.init(this::addRenderableWidget, this::setFocused, leftPos + PADDING, topPos + LIST_Y,
                WIDTH - PADDING * 2);
        return widget;
    }

    private void selectTab(boolean keep) {
        keepTab = keep;
        keepList.setVisible(keep);
        surplusList.setVisible(!keep);
        TargetListKind kind = keep ? TargetListKind.KEEP : TargetListKind.SURPLUS;
        menu.setActiveList(kind);
        PacketDistributor.sendToServer(TargetEditPayload.of(menu.containerId, kind.ordinal(),
                TargetEditPayload.Op.SELECT, -1, "", 0, false));
    }

    private TargetListWidget activeList() {
        return keepTab ? keepList : surplusList;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SupplySnapshot snapshot = menu.getSnapshot();
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            settings = snapshot;
        }
        redstoneButton.icon(RedstoneIcons.of(settings.redstoneMode()));
        redstoneButton.setTooltipText(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(settings.redstoneMode().translationKey())));
        craftButton.setBadge(settings.craftMissing() ? ScreenStyle.SUCCESS : ScreenStyle.DANGER);
        craftButton.setTooltipText(Component.translatable("gui.tccolonybridge.supply.craft",
                Component.translatable(settings.craftMissing() ? "gui.tccolonybridge.on" : "gui.tccolonybridge.off")));
        keepList.tick();
        surplusList.tick();
    }

    private void cycleRedstone() {
        settings = settings.withRedstone(settings.redstoneMode().next());
        sendSettings();
    }

    private void toggleCraft() {
        settings = settings.withCraftMissing(!settings.craftMissing());
        sendSettings();
    }

    private void sendSettings() {
        PacketDistributor.sendToServer(new SupplyConfigPayload(menu.containerId, settings.redstoneMode().ordinal(),
                settings.craftMissing()));
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        SupplySnapshot snap = menu.getSnapshot();
        int x = leftPos;
        int y = topPos;
        int inner = WIDTH - PADDING * 2;
        toolbar.render(g);
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

        int tabWidth = (inner - 4) / 2;
        tab(g, x + PADDING, y + TABS_Y, tabWidth, "gui.tccolonybridge.supply.tab_keep", keepTab, mouseX, mouseY);
        tab(g, x + PADDING + tabWidth + 4, y + TABS_Y, tabWidth, "gui.tccolonybridge.supply.tab_surplus", !keepTab,
                mouseX, mouseY);
        String rule = keepTab ? "gui.tccolonybridge.supply.keep_hint" : "gui.tccolonybridge.supply.surplus_hint";
        ScreenStyle.drawFitted(g, font, Component.translatable(rule), x + PADDING, y + LIST_Y - 11, inner - 50,
                ScreenStyle.TEXT);
        activeList().render(g, mouseX, mouseY);

        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.inventory"),
                x + ColonySupplyMenu.INVENTORY_X - 1, y + ColonySupplyMenu.INVENTORY_Y - 11, inner, ScreenStyle.TEXT);
        for (Slot slot : menu.slots) {
            ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    /** Aba de texto (as duas listas); a aberta fica com a cor de destaque. */
    private void tab(GuiGraphics g, int x, int y, int width, String key, boolean selected, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + TAB_HEIGHT;
        ScreenStyle.inset(g, x, y, width, TAB_HEIGHT, selected ? ScreenStyle.HOVER : hover ? ScreenStyle.PANEL : ScreenStyle.SLOT);
        Component label = Component.translatable(key);
        int textWidth = Math.min(font.width(label), width - 6);
        ScreenStyle.drawFitted(g, font, label, x + (width - textWidth) / 2, y + 3, width - 6,
                selected ? ScreenStyle.ACCENT : ScreenStyle.TEXT_MUTED);
    }

    /** Título e inventário já são desenhados em renderBg. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tip = activeList().tooltip(mouseX, mouseY);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int inner = WIDTH - PADDING * 2;
        int tabWidth = (inner - 4) / 2;
        double tabY = mouseY - topPos - TABS_Y;
        if (tabY >= 0 && tabY < TAB_HEIGHT) {
            double tabX = mouseX - leftPos - PADDING;
            if (tabX >= 0 && tabX < tabWidth) {
                selectTab(true);
                return true;
            }
            if (tabX >= tabWidth + 4 && tabX < inner) {
                selectTab(false);
                return true;
            }
        }
        if (activeList().mouseClicked(mouseX, mouseY, button, menu.getCarried())) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return activeList().mouseScrolled(mouseX, mouseY, scrollY, hasShiftDown(), hasControlDown())
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Com uma caixa de texto em foco, as teclas vão para ela (senão "E" fecharia a tela no meio da digitação). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return activeList().keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Lista aberta, para o JEI saber onde soltar itens. */
    public TargetListWidget listWidget() {
        return activeList();
    }

    /** Áreas fora da janela ocupadas pela tela (a barra lateral), para o JEI não desenhar por cima. */
    public List<Rect2i> extraAreas() {
        return List.of(toolbar.area());
    }

    /** O que só o Abastecedor mostra por linha: cor da situação e a dica com a regra e o armazém. */
    private final class Info implements TargetListWidget.LineInfo {

        private final boolean keep;

        Info(boolean keep) {
            this.keep = keep;
        }

        @Override
        public int color(int index) {
            return StatusColors.of(menu.getSnapshot().line(keep, index).status().severity());
        }

        @Override
        public void appendTooltip(int index, List<Component> lines) {
            List<TargetLineView> views = menu.targetLists().lines(keep ? TargetListKind.KEEP : TargetListKind.SURPLUS);
            if (index >= views.size()) {
                return;
            }
            TargetLineView view = views.get(index);
            SupplyLineStat stat = menu.getSnapshot().line(keep, index);
            String rule = keep ? "gui.tccolonybridge.supply.keep_rule"
                    : view.all() ? "gui.tccolonybridge.supply.surplus_all_rule" : "gui.tccolonybridge.supply.surplus_rule";
            lines.add(Component.translatable(rule, view.amount()).withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("gui.tccolonybridge.supply.current", stat.warehouse())
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable(stat.status().translationKey()).withStyle(ChatFormatting.GRAY));
        }
    }
}
