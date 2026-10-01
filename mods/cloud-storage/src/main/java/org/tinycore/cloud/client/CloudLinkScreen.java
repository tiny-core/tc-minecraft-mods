package org.tinycore.cloud.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.tinycore.cloud.block.NetworkAccess;
import org.tinycore.cloud.item.ItemCompatibility;
import org.tinycore.cloud.item.TransferRejection;
import org.tinycore.cloud.menu.CloudLinkMenu;
import org.tinycore.cloud.menu.LinkAction;
import org.tinycore.cloud.menu.LinkEntry;
import org.tinycore.cloud.menu.LinkHeader;
import org.tinycore.cloud.network.LinkActionPayload;
import org.tinycore.cloud.server.CloudStatus;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.ItemGrid;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;
import org.tinycore.core.grid.ItemListing;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela do TC Cloud Link: cabeçalho com a situação da nuvem, busca, grade do canal (grade genérica do core) e o
 * inventário. Barra lateral: ajuda, ordem, "mostrar incompatíveis", modo de acesso da rede e prioridade.
 *
 * <p>Cliques na grade: esquerdo tira um stack para o inventário, direito tira 1; com item no cursor, esquerdo
 * guarda tudo e direito guarda 1. Shift-clique no inventário guarda o stack (tratado pelo menu). A tela só pede;
 * o servidor decide e a grade se atualiza com o próximo pacote.
 *
 * <p>Por padrão só aparecem os itens que podem sair neste servidor; com o filtro ligado aparecem também os
 * incompatíveis, apagados e com o motivo no tooltip.
 */
public class CloudLinkScreen extends AbstractContainerScreen<CloudLinkMenu> {

    private static final int PADDING = 8;
    private static final int SEARCH_Y = 32;

    /** Preferências da sessão de jogo (não salvas): ordem e filtro. */
    private static ItemListing.Sort sort = ItemListing.Sort.AMOUNT;
    private static boolean showIncompatible;

    private SideToolbar toolbar;
    private IconButton sortButton;
    private IconButton incompatibleButton;
    private IconButton accessButton;
    private IconButton priorityUp;
    private IconButton priorityDown;
    private EditBox search;
    private ItemGrid<LinkEntry> grid;
    private List<LinkEntry> filtered = List.of();
    private int filteredFrom = -1;
    private boolean filteredShowAll;
    private int gridVersion;

    public CloudLinkScreen(CloudLinkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CloudLinkMenu.WIDTH;
        this.imageHeight = CloudLinkMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        IconButton help = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> {}).glyph("?")));
        help.setTooltipText(Component.translatable("gui.tccloud.help"));
        sortButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, this::cycleSort)));
        incompatibleButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, this::toggleIncompatible).glyph("!")));
        incompatibleButton.setTooltipText(Component.translatable("gui.tccloud.show_incompatible"));
        accessButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> send(LinkAction.CYCLE_ACCESS, ""))));
        priorityUp = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> send(LinkAction.PRIORITY_UP, "")).glyph("+")));
        priorityDown = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> send(LinkAction.PRIORITY_DOWN, "")).glyph("-")));
        toolbar.layout(leftPos, topPos, CloudLinkMenu.WIDTH);
        updateSortButton();
        incompatibleButton.setSelected(showIncompatible);

        String previous = search == null ? "" : search.getValue(); // mantém a busca ao redimensionar
        search = addRenderableWidget(new EditBox(font, leftPos + PADDING + 3, topPos + SEARCH_Y + 2,
                CloudLinkMenu.WIDTH - PADDING * 2 - 6, 10, Component.translatable("gui.tccloud.search")));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(ScreenStyle.TEXT);
        search.setHint(Component.translatable("gui.tccloud.search").withColor(ScreenStyle.TEXT_MUTED));
        search.setValue(previous);

        grid = new ItemGrid<>(leftPos + CloudLinkMenu.GRID_X, topPos + CloudLinkMenu.GRID_Y,
                CloudLinkMenu.COLUMNS, CloudLinkMenu.ROWS, new LinkEntryAdapter());
    }

    private void send(LinkAction action, String fingerprint) {
        PacketDistributor.sendToServer(new LinkActionPayload(menu.containerId, action.ordinal(), fingerprint));
    }

    private void cycleSort() {
        sort = sort.next();
        updateSortButton();
    }

    private void updateSortButton() {
        sortButton.glyph(sort == ItemListing.Sort.AMOUNT ? "#" : "A");
        sortButton.setTooltipText(Component.translatable(sort == ItemListing.Sort.AMOUNT
                ? "gui.tccloud.sort.amount" : "gui.tccloud.sort.name"));
    }

    private void toggleIncompatible() {
        showIncompatible = !showIncompatible;
        incompatibleButton.setSelected(showIncompatible);
    }

    /** Lista da grade: refeita só quando chega pacote novo ou o filtro muda. */
    private List<LinkEntry> visibleEntries() {
        int version = menu.view().version();
        if (version != filteredFrom || showIncompatible != filteredShowAll) {
            filteredFrom = version;
            filteredShowAll = showIncompatible;
            gridVersion++;
            List<LinkEntry> result = new ArrayList<>();
            for (LinkEntry e : menu.view().entries()) {
                if (showIncompatible || e.status() == ItemCompatibility.OK.ordinal()) result.add(e);
            }
            filtered = result;
        }
        return filtered;
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int inner = CloudLinkMenu.WIDTH - PADDING * 2;
        LinkHeader header = menu.view().header();
        updateToolbar(header);
        toolbar.render(g);
        ScreenStyle.window(g, x, y, CloudLinkMenu.WIDTH, CloudLinkMenu.HEIGHT);

        CloudStatus status = statusOf(header);
        int statusWidth = ScreenStyle.drawFittedRight(g, font, Component.translatable(status.translationKey()),
                x + CloudLinkMenu.WIDTH - PADDING, y + 7, inner / 2, ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + CloudLinkMenu.WIDTH - PADDING - statusWidth - 10, y + 7, statusColor(status));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, y + 7, inner - statusWidth - 16, ScreenStyle.TITLE);
        Line line = infoLine(header, status);
        if (line != null) ScreenStyle.drawFitted(g, font, line.text(), x + PADDING, y + 19, inner, line.color());
        ScreenStyle.inset(g, x + PADDING, y + SEARCH_Y, inner, 13, ScreenStyle.SLOT);

        grid.update(gridVersion, visibleEntries(), search.getValue(), sort);
        grid.render(g, font, mouseX, mouseY);

        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccloud.inventory"),
                x + CloudLinkMenu.INVENTORY_X - 1, y + CloudLinkMenu.INVENTORY_Y - 12, inner, ScreenStyle.TEXT);
        for (Slot slot : menu.slots) {
            ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    private void updateToolbar(LinkHeader header) {
        NetworkAccess access = NetworkAccess.byId(header.access());
        accessButton.glyph(switch (access) {
            case TERMINAL_ONLY -> "T";
            case DEPOSIT_ONLY -> "D";
            case FULL -> "R";
        });
        accessButton.setTooltipText(Component.translatable("gui.tccloud.access", Component.translatable(access.translationKey())));
        Component priority = Component.translatable("gui.tccloud.priority", header.priority());
        priorityUp.setTooltipText(priority);
        priorityDown.setTooltipText(priority);
    }

    private static CloudStatus statusOf(LinkHeader header) {
        CloudStatus[] all = CloudStatus.values();
        return header.status() >= 0 && header.status() < all.length ? all[header.status()] : CloudStatus.DISABLED;
    }

    private static int statusColor(CloudStatus status) {
        return switch (status) {
            case ACTIVE -> ScreenStyle.SUCCESS;
            case CONNECTING -> ScreenStyle.INFO;
            case BUSY, READ_ONLY -> ScreenStyle.WARNING;
            case UNAVAILABLE -> ScreenStyle.DANGER;
            case DISABLED -> ScreenStyle.TEXT_MUTED;
        };
    }

    private record Line(Component text, int color) {}

    /** Segunda linha do cabeçalho: a informação mais importante agora. */
    private static @Nullable Line infoLine(LinkHeader header, CloudStatus status) {
        TransferRejection[] rejections = TransferRejection.values();
        if (header.rejection() >= 0 && header.rejection() < rejections.length) {
            return new Line(Component.translatable(rejections[header.rejection()].translationKey()), ScreenStyle.DANGER);
        }
        if (!header.conflict().isEmpty()) {
            return new Line(Component.translatable("gui.tccloud.conflict", header.conflict()), ScreenStyle.WARNING);
        }
        if (status == CloudStatus.BUSY) {
            return new Line(Component.translatable("gui.tccloud.busy", header.detail()), ScreenStyle.WARNING);
        }
        if (status == CloudStatus.READ_ONLY && !header.detail().isEmpty()) {
            return new Line(Component.translatable("gui.tccloud.read_only_reason", header.detail()), ScreenStyle.WARNING);
        }
        if (status == CloudStatus.ACTIVE && NetworkAccess.byId(header.access()).mounts() && !header.networkUp()) {
            return new Line(Component.translatable("gui.tccloud.network_down"), ScreenStyle.TEXT_MUTED);
        }
        return null;
    }

    /** O título já é desenhado no {@link #renderBg}. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        LinkEntry hovered = menu.getCarried().isEmpty() ? grid.entryAt(mouseX, mouseY) : null;
        if (hovered == null) {
            renderTooltip(g, mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (!hovered.icon().isEmpty()) {
            lines.addAll(getTooltipFromItem(minecraft, hovered.icon()));
        } else {
            lines.add(Component.literal(hovered.name()));
            lines.add(Component.literal(hovered.itemId()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("gui.tccloud.stored", hovered.amount()).withStyle(ChatFormatting.AQUA));
        ItemCompatibility[] all = ItemCompatibility.values();
        if (hovered.status() != ItemCompatibility.OK.ordinal() && hovered.status() >= 0 && hovered.status() < all.length) {
            lines.add(Component.translatable(all[hovered.status()].translationKey()).withStyle(ChatFormatting.RED));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && search.isMouseOver(mouseX, mouseY)) {
            search.setValue(""); // clique direito na busca limpa, como no AE2
            return true;
        }
        if (!search.isMouseOver(mouseX, mouseY)) search.setFocused(false);
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        if (grid.isOver(mouseX, mouseY) && (left || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            if (!menu.getCarried().isEmpty()) {
                send(left ? LinkAction.DEPOSIT_CARRIED : LinkAction.DEPOSIT_ONE, "");
            } else {
                LinkEntry entry = grid.entryAt(mouseX, mouseY);
                if (entry != null && entry.status() == ItemCompatibility.OK.ordinal()) {
                    send(left ? LinkAction.TAKE_STACK : LinkAction.TAKE_ONE, entry.fingerprint());
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (grid.isOver(mouseX, mouseY)) {
            grid.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Com a busca em foco, as teclas vão para ela (senão "E" fecharia a tela). Esc continua fechando. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
