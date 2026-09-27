package org.tinycore.colonybridge.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.block.RedstoneMode;
import org.tinycore.colonybridge.client.ui.FlatButton;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.RequestLine;
import org.tinycore.colonybridge.logic.RequestOutcome;
import org.tinycore.colonybridge.menu.BridgeSnapshot;
import org.tinycore.colonybridge.menu.ColonyBridgeMenu;
import org.tinycore.colonybridge.network.BridgeSettingsPayload;

import java.util.List;

/**
 * Tela da ponte (só cliente): estado, colônia, ajustes e a lista de pedidos com o resultado de cada um.
 * <p>
 * Não tem textura: tudo é desenhado com retângulos e texto usando {@link UiColors}.
 * Os dados vêm do {@link BridgeSnapshot} guardado no menu; os botões mandam um
 * {@link BridgeSettingsPayload} e o servidor decide se aplica.
 */
public class ColonyBridgeScreen extends AbstractContainerScreen<ColonyBridgeMenu> {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 212;
    private static final int LIST_TOP = 58;
    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 6;
    private static final int PADDING = 8;

    private FlatButton craftingButton;
    private FlatButton redstoneButton;
    private int scroll;

    /** Estado local dos ajustes: muda na hora do clique e é corrigido pelo próximo snapshot. */
    private boolean craftingEnabled;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;
    private BridgeSnapshot lastSeen;

    public ColonyBridgeScreen(ColonyBridgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = (WIDTH - PADDING * 3) / 2;
        craftingButton = addRenderableWidget(new FlatButton(leftPos + PADDING, topPos + 34, buttonWidth, 16,
                Component.empty(), this::toggleCrafting));
        redstoneButton = addRenderableWidget(new FlatButton(leftPos + PADDING * 2 + buttonWidth, topPos + 34,
                buttonWidth, 16, Component.empty(), this::cycleRedstone));
        lastSeen = null; // força copiar o snapshot atual para os botões
    }

    /** Chamado a cada tick do cliente: aplica um snapshot novo aos botões. */
    @Override
    protected void containerTick() {
        super.containerTick();
        BridgeSnapshot snapshot = menu.getSnapshot();
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            craftingEnabled = snapshot.craftingEnabled();
            redstoneMode = snapshot.redstoneMode();
            scroll = Math.min(scroll, maxScroll());
        }
        craftingButton.setMessage(Component.translatable("gui.tccolonybridge.crafting",
                Component.translatable(craftingEnabled ? "gui.tccolonybridge.on" : "gui.tccolonybridge.off")));
        redstoneButton.setMessage(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(redstoneMode.translationKey())));
    }

    private void toggleCrafting() {
        craftingEnabled = !craftingEnabled;
        sendSettings();
    }

    private void cycleRedstone() {
        redstoneMode = redstoneMode.next();
        sendSettings();
    }

    private void sendSettings() {
        PacketDistributor.sendToServer(
                new BridgeSettingsPayload(menu.containerId, craftingEnabled, redstoneMode.ordinal()));
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
        int statusWidth = font.width(status);
        int statusX = x + WIDTH - PADDING - statusWidth;
        g.fill(statusX - 9, y + 22, statusX - 4, y + 27, statusColor(snap.status()));
        g.drawString(font, status, statusX, y + 20, UiColors.TEXT, false);

        renderRequests(g, snap, x, y + LIST_TOP, mouseX, mouseY);
        renderFooter(g, snap, x, y + HEIGHT - 14);
    }

    private void renderRequests(GuiGraphics g, BridgeSnapshot snap, int x, int y, int mouseX, int mouseY) {
        List<RequestLine> lines = snap.lines();
        if (lines.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.tccolonybridge.no_requests"),
                    x + WIDTH / 2, y + ROW_HEIGHT * VISIBLE_ROWS / 2 - 4, UiColors.TEXT_MUTED);
            return;
        }
        int rowWidth = WIDTH - PADDING * 2 - 4;
        for (int i = 0; i < VISIBLE_ROWS && scroll + i < lines.size(); i++) {
            RequestLine line = lines.get(scroll + i);
            int rowX = x + PADDING;
            int rowY = y + i * ROW_HEIGHT;
            boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            g.fill(rowX, rowY, rowX + rowWidth, rowY + ROW_HEIGHT - 2, hovered ? UiColors.PANEL_HOVER : UiColors.PANEL);
            g.renderItem(line.icon(), rowX + 2, rowY + 2);

            int textX = rowX + 22;
            int textWidth = rowWidth - 24;
            String count = "x" + line.count();
            g.drawString(font, count, rowX + rowWidth - 2 - font.width(count), rowY + 2, UiColors.TEXT_MUTED, false);
            g.drawString(font, Language.getInstance().getVisualOrder(
                    font.substrByWidth(line.label(), textWidth - font.width(count) - 4)),
                    textX, rowY + 2, UiColors.TEXT, false);
            g.drawString(font, Component.translatable(line.outcome().translationKey()),
                    textX, rowY + 11, outcomeColor(line.outcome()), false);
        }
        renderScrollbar(g, lines.size(), x + WIDTH - PADDING - 2, y);
    }

    private void renderScrollbar(GuiGraphics g, int total, int x, int y) {
        if (total <= VISIBLE_ROWS) {
            return;
        }
        int trackHeight = ROW_HEIGHT * VISIBLE_ROWS - 2;
        int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / total);
        int thumbY = y + (trackHeight - thumbHeight) * scroll / maxScroll();
        g.fill(x, y, x + 2, y + trackHeight, UiColors.BORDER);
        g.fill(x, thumbY, x + 2, thumbY + thumbHeight, UiColors.ACCENT);
    }

    private void renderFooter(GuiGraphics g, BridgeSnapshot snap, int x, int y) {
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
        renderRowTooltip(g, mouseX, mouseY);
    }

    /** Descrição completa ao passar o mouse sobre uma linha (o texto na linha pode estar cortado). */
    private void renderRowTooltip(GuiGraphics g, int mouseX, int mouseY) {
        List<RequestLine> lines = menu.getSnapshot().lines();
        int rowX = leftPos + PADDING;
        int relY = mouseY - (topPos + LIST_TOP);
        if (mouseX < rowX || mouseX >= rowX + WIDTH - PADDING * 2 - 4 || relY < 0) {
            return;
        }
        int index = scroll + relY / ROW_HEIGHT;
        if (relY / ROW_HEIGHT >= VISIBLE_ROWS || index >= lines.size()) {
            return;
        }
        RequestLine line = lines.get(index);
        g.renderComponentTooltip(font, List.of(line.label(),
                Component.translatable("gui.tccolonybridge.amount", line.count()),
                Component.translatable(line.outcome().translationKey())), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    private int maxScroll() {
        return Math.max(0, menu.getSnapshot().lines().size() - VISIBLE_ROWS);
    }

    // ---------------------------------------------------------------- cores

    private static int statusColor(BridgeStatus status) {
        return switch (status) {
            case WORKING -> UiColors.HIGHLIGHT;
            case IDLE -> UiColors.SUCCESS;
            case STARTING, PAUSED -> UiColors.WARNING;
            case OFFLINE, INVALID_CABLE -> UiColors.TEXT_MUTED;
            case NO_COLONY, NO_PERMISSION, NO_WAREHOUSE -> UiColors.DANGER;
        };
    }

    private static int outcomeColor(RequestOutcome outcome) {
        return switch (outcome) {
            case DELIVERED, WAITING_COURIER -> UiColors.SUCCESS;
            case CRAFT_STARTED, CRAFTING -> UiColors.HIGHLIGHT;
            case QUEUED, OTHER_BRIDGE -> UiColors.TEXT_MUTED;
            case RACKS_FULL, CRAFTING_DISABLED -> UiColors.WARNING;
            case NO_STOCK, NOT_CRAFTABLE -> UiColors.DANGER;
        };
    }
}
