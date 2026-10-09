package org.tinycore.colonybridge.client.terminal;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.tinycore.colonybridge.client.tablet.TabletTabBar;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.terminal.TerminalAction;
import org.tinycore.colonybridge.menu.terminal.WarehouseEntry;
import org.tinycore.colonybridge.menu.terminal.WarehouseTerminalMenu;
import org.tinycore.colonybridge.network.WarehouseActionPayload;
import org.tinycore.core.TcCoreClientConfig;
import org.tinycore.core.client.ui.GridHeightButton;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.ItemGrid;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;
import org.tinycore.core.client.ui.TcIcons;
import org.tinycore.core.grid.ItemListing;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela do Terminal do Armazém (só cliente), com a estrutura do terminal do AE2 e as cores da marca:
 * busca, grade com todos os itens dos racks ({@link ItemGrid}), bancada 3×3 (como o Crafting Terminal)
 * e o inventário embaixo. Os slots da bancada são do menu; aqui se desenha a estrutura do Crafting Terminal
 * do AE2 — painel afundado, grade, dois botões pequenos (▲ devolve ao armazém, ▼ manda para o inventário),
 * seta e o resultado numa moldura maior.
 * <p>
 * Cliques na grade, como no AE2: esquerdo tira um stack para o cursor, direito tira meio stack, Shift manda
 * direto para o inventário; com item no cursor, esquerdo guarda tudo e direito guarda um. Shift-clique no
 * inventário guarda o stack no armazém (isso é tratado pelo menu). A tela só manda o pedido; o servidor
 * decide e a grade se atualiza com o próximo pacote.
 * <p>
 * <b>Altura variável:</b> o botão da barra lateral ({@link GridHeightButton}) escolhe 5 linhas (padrão), 8 ou o que
 * couber na janela, como o "estilo do terminal" do AE2; a escolha vale para todos os terminais TC. Janela do jogo
 * pequena reduz a grade até {@code MIN_ROWS}. Como os slots têm posição fixa, a parte de baixo (bancada e inventário) fica no lugar
 * de sempre e as linhas extras crescem para cima: {@link #extra} pixels acima de {@code topPos}.
 */
public class WarehouseTerminalScreen extends AbstractContainerScreen<WarehouseTerminalMenu> {

    private static final int WIDTH = 222;
    /** Altura com a grade mínima; cada linha extra soma 18 px acima. */
    private static final int HEIGHT = WarehouseTerminalMenu.HOTBAR_Y + 25;
    /** Espaço livre deixado acima e abaixo da janela ao calcular quantas linhas cabem. */
    private static final int SCREEN_MARGIN = 8;
    /** Botões pequenos ao lado da grade da bancada (▲ armazém, ▼ inventário), como no AE2. */
    private static final int SMALL_BUTTON = 9;
    private static final int PADDING = 8;
    private static final int SEARCH_Y = 32;

    /** Ordem escolhida; {@code static} = lembrada entre aberturas da tela até fechar o jogo. */
    private static ItemListing.Sort sort = ItemListing.Sort.AMOUNT;

    private SideToolbar toolbar;
    private IconButton sortButton;
    private EditBox search;
    private ItemGrid<WarehouseEntry> grid;
    /** Pixels acima de {@code topPos} ocupados pelas linhas extras da grade. */
    private int extra;

    public WarehouseTerminalScreen(WarehouseTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int tabs = menu.tabletView() == null ? 0 : TabletTabBar.HEIGHT; // abas do tablet acima da janela
        int rows = TcCoreClientConfig.gridHeight().rows(WarehouseTerminalMenu.MIN_ROWS, WarehouseTerminalMenu.MAX_ROWS,
                height - tabs - SCREEN_MARGIN * 2 - HEIGHT, 18);
        extra = (rows - WarehouseTerminalMenu.MIN_ROWS) * 18;
        // Janela inteira (com as abas) centralizada; slots continuam em topPos.
        topPos = (height - tabs - HEIGHT - extra) / 2 + extra + tabs;
        int top = topPos - extra;
        TabletTabBar.add(menu.tabletView(), this::addRenderableWidget, leftPos, top);

        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        IconButton help = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> {}).sprite(TcIcons.HELP)));
        help.setTooltipText(Component.translatable("gui.tccolonybridge.help.terminal"));
        sortButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, this::cycleSort)));
        addRenderableWidget(toolbar.add(GridHeightButton.create(this::rebuildWidgets)));
        toolbar.layout(leftPos, top, WIDTH);
        updateSortButton();

        int buttonsX = leftPos + WarehouseTerminalMenu.CRAFT_X + 57;
        int buttonsY = topPos + WarehouseTerminalMenu.CRAFT_Y - 1;
        IconButton toWarehouse = addRenderableWidget(new IconButton(buttonsX, buttonsY, SMALL_BUTTON,
                () -> sendGridAction(TerminalAction.CLEAR_GRID)).glyph("▲"));
        toWarehouse.setTooltipText(Component.translatable("gui.tccolonybridge.terminal.clear_grid"));
        IconButton toInventory = addRenderableWidget(new IconButton(buttonsX, buttonsY + SMALL_BUTTON + 2, SMALL_BUTTON,
                () -> sendGridAction(TerminalAction.GRID_TO_INVENTORY)).glyph("▼"));
        toInventory.setTooltipText(Component.translatable("gui.tccolonybridge.terminal.grid_to_inventory"));

        String previous = search == null ? "" : search.getValue(); // mantém a busca ao redimensionar a janela
        search = addRenderableWidget(new EditBox(font, leftPos + PADDING + 3, top + SEARCH_Y + 2,
                WIDTH - PADDING * 2 - 6, 10, Component.translatable("gui.tccolonybridge.terminal.search")));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(ScreenStyle.TEXT);
        search.setHint(Component.translatable("gui.tccolonybridge.terminal.search").withColor(ScreenStyle.TEXT_MUTED));
        search.setValue(previous);

        grid = new ItemGrid<>(leftPos + WarehouseTerminalMenu.GRID_X, top + WarehouseTerminalMenu.GRID_Y,
                WarehouseTerminalMenu.COLUMNS, rows, new WarehouseEntryAdapter());
    }

    private void sendGridAction(TerminalAction action) {
        PacketDistributor.sendToServer(new WarehouseActionPayload(menu.containerId, action.ordinal(), ItemStack.EMPTY));
    }

    private void cycleSort() {
        sort = sort.next();
        updateSortButton();
    }

    private void updateSortButton() {
        sortButton.sprite(sort == ItemListing.Sort.AMOUNT ? TcIcons.SORT_AMOUNT : TcIcons.SORT_NAME);
        sortButton.setTooltipText(Component.translatable(sort == ItemListing.Sort.AMOUNT
                ? "gui.tccolonybridge.terminal.sort.amount" : "gui.tccolonybridge.terminal.sort.name"));
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int top = topPos - extra; // cabeçalho, busca e grade sobem junto com as linhas extras
        int inner = WIDTH - PADDING * 2;
        toolbar.render(g);
        ScreenStyle.window(g, x, top, WIDTH, HEIGHT + extra);
        // Estado à direita (Online, Sem Ponte na rede, Offline...); o título usa o espaço que sobrar.
        BridgeStatus status = menu.getView().status();
        int statusWidth = ScreenStyle.drawFittedRight(g, font, Component.translatable(status.guiKey()),
                x + WIDTH - PADDING, top + 7, inner / 2, ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + WIDTH - PADDING - statusWidth - 10, top + 7, StatusColors.of(status));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, top + 7, inner - statusWidth - 16, ScreenStyle.TITLE);
        ScreenStyle.drawFitted(g, font, Component.literal(menu.getColonyName()), x + PADDING, top + 19, inner,
                ScreenStyle.INFO);
        ScreenStyle.inset(g, x + PADDING, top + SEARCH_Y, inner, 13, ScreenStyle.SLOT);

        grid.update(menu.getView().version(), menu.getView().entries(), search.getValue(), sort);
        grid.render(g, font, mouseX, mouseY);

        craftingPanel(g, x, y, inner);
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.inventory"),
                x + WarehouseTerminalMenu.INVENTORY_X - 1, y + WarehouseTerminalMenu.INVENTORY_Y - 12, inner,
                ScreenStyle.TEXT);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (i != WarehouseTerminalMenu.RESULT_SLOT) { // o resultado tem moldura própria
                ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
            }
        }
    }

    /**
     * Estrutura do Crafting Terminal do AE2: título da seção, painel afundado ocupando a largura, seta até o
     * resultado e o resultado numa moldura de 26×26 (os slots da grade são desenhados com os demais).
     */
    private void craftingPanel(GuiGraphics g, int x, int y, int inner) {
        int craftY = y + WarehouseTerminalMenu.CRAFT_Y;
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.terminal.crafting"), x + PADDING,
                craftY - 14, inner, ScreenStyle.TEXT);
        ScreenStyle.inset(g, x + PADDING, craftY - 5, inner, 64, ScreenStyle.PANEL);
        int arrowX = x + WarehouseTerminalMenu.CRAFT_X + 57 + SMALL_BUTTON + 8;
        arrow(g, arrowX, craftY + 26, x + WarehouseTerminalMenu.RESULT_X - 5 - 6 - arrowX);
        ScreenStyle.inset(g, x + WarehouseTerminalMenu.RESULT_X - 5, y + WarehouseTerminalMenu.RESULT_Y - 5, 26, 26,
                ScreenStyle.SLOT);
    }

    /** Seta grossa "grade → resultado", como a do AE2: haste e ponta feitas de retângulos. */
    private static void arrow(GuiGraphics g, int x, int centerY, int length) {
        int head = 6;
        g.fill(x, centerY - 2, x + length - head, centerY + 2, ScreenStyle.TEXT_MUTED);
        for (int i = 0; i < head; i++) {
            g.fill(x + length - head + i, centerY - head + i, x + length - head + i + 1, centerY + head - i,
                    ScreenStyle.TEXT_MUTED);
        }
    }

    /** O título já é desenhado no {@link #renderBg}; aqui não vai nada (evita o título padrão duplicado). */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        WarehouseEntry hovered = menu.getCarried().isEmpty() ? grid.entryAt(mouseX, mouseY) : null;
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hovered.item()));
            lines.add(Component.translatable("gui.tccolonybridge.terminal.stored", hovered.count())
                    .withStyle(ChatFormatting.AQUA));
            g.renderTooltip(font, lines, hovered.item().getTooltipImage(), hovered.item(), mouseX, mouseY);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && search.isMouseOver(mouseX, mouseY)) {
            search.setValue(""); // clique direito na busca limpa, como no AE2
            return true;
        }
        if (!search.isMouseOver(mouseX, mouseY)) {
            search.setFocused(false);
        }
        if (grid.isOver(mouseX, mouseY) && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            clickGrid(grid.entryAt(mouseX, mouseY), button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Traduz o clique em ação e manda ao servidor. Célula vazia só serve para guardar o que está no cursor. */
    private void clickGrid(@Nullable WarehouseEntry entry, boolean right) {
        TerminalAction action;
        ItemStack item = ItemStack.EMPTY;
        if (!menu.getCarried().isEmpty()) {
            action = right ? TerminalAction.INSERT_ONE : TerminalAction.INSERT_CARRIED;
        } else if (entry == null) {
            return;
        } else {
            action = hasShiftDown() ? TerminalAction.TAKE_TO_INVENTORY
                    : right ? TerminalAction.TAKE_HALF : TerminalAction.TAKE_STACK;
            item = entry.item();
        }
        PacketDistributor.sendToServer(new WarehouseActionPayload(menu.containerId, action.ordinal(), item));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (grid.isOver(mouseX, mouseY)) {
            grid.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Com a busca em foco, as teclas vão para ela (senão "E" fecharia a tela e números trocariam a hotbar).
     * Esc continua fechando a tela.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Áreas fora da janela ocupadas pela tela (a barra lateral), para o JEI não desenhar por cima. */
    public List<Rect2i> extraAreas() {
        return List.of(toolbar.area(), new Rect2i(leftPos, topPos - extra, imageWidth, extra));
    }

    /**
     * A janela vai além de {@code topPos} para cima (linhas extras): clicar ali não é "fora da janela" (fora,
     * o jogo jogaria no chão o item do cursor).
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        return mouseX < guiLeft || mouseY < guiTop - extra
                || mouseX >= guiLeft + imageWidth || mouseY >= guiTop + imageHeight;
    }

    /** Área da célula da grade sob o mouse e o item dela, para o JEI (teclas R/U); null fora da grade. */
    public @Nullable ItemGrid.Hit gridHit(double mouseX, double mouseY) {
        return grid.hitAt(mouseX, mouseY);
    }
}
