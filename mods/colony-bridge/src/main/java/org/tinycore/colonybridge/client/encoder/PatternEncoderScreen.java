package org.tinycore.colonybridge.client.encoder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.tinycore.colonybridge.client.tablet.TabletTabBar;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.logic.encoder.EncoderState;
import org.tinycore.colonybridge.menu.encoder.EncoderLine;
import org.tinycore.colonybridge.menu.encoder.EncoderSnapshot;
import org.tinycore.colonybridge.menu.encoder.PatternEncoderMenu;
import org.tinycore.colonybridge.network.EncoderActionPayload;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;
import org.tinycore.core.client.ui.TcIcons;
import org.tinycore.core.client.ui.UiFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela do TC Pattern Encoder (só cliente), na estrutura das outras telas do mod e com as cores da marca: lista dos
 * pedidos da colônia sem padrão no AE2 (ícone, nome, quantidade pedida e situação), slot de Blank Patterns, nove
 * slots de saída e o inventário.
 * <p>
 * Clique numa linha "pronta" codifica aquele item; o botão da barra lateral codifica todas. A tela só pede: o
 * servidor escolhe a receita, gasta o Blank Pattern e manda a lista nova. O tooltip da linha mostra os ingredientes.
 * Aberta pelo tablet, ganha a barra de abas em cima.
 */
public class PatternEncoderScreen extends AbstractContainerScreen<PatternEncoderMenu> {

    private static final int PADDING = 8;
    private static final int LIST_WIDTH = PatternEncoderMenu.WIDTH - PADDING * 2 - 6; // 6 = barra de rolagem
    private static final ResourceLocation PATTERN_ICON = ResourceLocation.fromNamespaceAndPath("ae2", "crafting_pattern");

    private SideToolbar toolbar;
    private int firstRow;

    public PatternEncoderScreen(PatternEncoderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PatternEncoderMenu.WIDTH;
        this.imageHeight = PatternEncoderMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos += TabletTabBar.offset(menu.tabletView()); // abas do tablet acima da janela
        TabletTabBar.add(menu.tabletView(), this::addRenderableWidget, leftPos, topPos);
        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        IconButton help = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> {}).sprite(TcIcons.HELP)));
        help.setTooltipText(Component.translatable("gui.tccolonybridge.help.encoder"));
        IconButton encodeAll = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> send(ItemStack.EMPTY))
                .icon(new ItemStack(BuiltInRegistries.ITEM.get(PATTERN_ICON)))));
        encodeAll.setTooltipText(Component.translatable("gui.tccolonybridge.encoder.encode_all"));
        toolbar.layout(leftPos, topPos, PatternEncoderMenu.WIDTH);
    }

    private void send(ItemStack item) {
        PacketDistributor.sendToServer(new EncoderActionPayload(menu.containerId, item));
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        EncoderSnapshot snap = menu.getSnapshot();
        int x = leftPos;
        int y = topPos;
        int inner = PatternEncoderMenu.WIDTH - PADDING * 2;
        toolbar.render(g);
        ScreenStyle.window(g, x, y, PatternEncoderMenu.WIDTH, PatternEncoderMenu.HEIGHT);

        int statusWidth = ScreenStyle.drawFittedRight(g, font, Component.translatable(snap.status().guiKey()),
                x + PatternEncoderMenu.WIDTH - PADDING, y + 7, inner / 2, ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + PatternEncoderMenu.WIDTH - PADDING - statusWidth - 10, y + 7,
                StatusColors.of(snap.status()));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, y + 7, inner - statusWidth - 16, ScreenStyle.TITLE);
        int hiddenWidth = snap.hidden() == 0 ? 0 : ScreenStyle.drawFittedRight(g, font,
                Component.translatable("gui.tccolonybridge.encoder.hidden", snap.hidden()),
                x + PatternEncoderMenu.WIDTH - PADDING, y + 19, inner / 2, ScreenStyle.TEXT_MUTED);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony") : Component.literal(snap.colonyName());
        ScreenStyle.drawFitted(g, font, colony, x + PADDING, y + 19, inner - hiddenWidth - 6, ScreenStyle.INFO);

        drawList(g, snap.lines(), mouseX, mouseY);

        int labelY = y + PatternEncoderMenu.SLOTS_Y - 11;
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.encoder.blanks"),
                x + PatternEncoderMenu.BLANK_X - 1, labelY, 36, ScreenStyle.TEXT);
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.encoder.outputs"),
                x + PatternEncoderMenu.OUTPUT_X - 1, labelY, 9 * 18, ScreenStyle.TEXT);
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.inventory"),
                x + PatternEncoderMenu.INVENTORY_X - 1, y + PatternEncoderMenu.INVENTORY_Y - 12, inner, ScreenStyle.TEXT);
        for (Slot slot : menu.slots) {
            ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    private void drawList(GuiGraphics g, List<EncoderLine> lines, int mouseX, int mouseY) {
        int x = leftPos + PADDING;
        int y = topPos + PatternEncoderMenu.LIST_Y;
        int rows = PatternEncoderMenu.VISIBLE_ROWS;
        int height = rows * PatternEncoderMenu.ROW_HEIGHT;
        ScreenStyle.inset(g, x, y - 2, LIST_WIDTH, height + 4, ScreenStyle.PANEL);
        firstRow = Math.max(0, Math.min(firstRow, lines.size() - rows));
        if (lines.isEmpty()) {
            ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.encoder.empty"), x + 6,
                    y + height / 2 - 4, LIST_WIDTH - 12, ScreenStyle.TEXT_MUTED);
        }
        for (int i = 0; i < rows && firstRow + i < lines.size(); i++) {
            drawRow(g, lines.get(firstRow + i), x, y + i * PatternEncoderMenu.ROW_HEIGHT, mouseX, mouseY);
        }
        ScreenStyle.scrollbar(g, x + LIST_WIDTH + 2, y - 2, height + 4, firstRow, rows, lines.size());
    }

    private void drawRow(GuiGraphics g, EncoderLine line, int x, int y, int mouseX, int mouseY) {
        boolean hovered = isOverRow(mouseX, mouseY, x, y);
        if (hovered && line.state() == EncoderState.READY) {
            g.fill(x + 1, y, x + LIST_WIDTH - 1, y + PatternEncoderMenu.ROW_HEIGHT, ScreenStyle.HOVER);
        }
        g.renderItem(line.result(), x + 3, y + 2);
        String amount = "×" + UiFormat.compact(line.amount());
        int amountWidth = font.width(amount);
        g.drawString(font, amount, x + LIST_WIDTH - 4 - amountWidth, y + 2, ScreenStyle.TEXT_MUTED, false);
        ScreenStyle.drawFitted(g, font, line.result().getHoverName(), x + 23, y + 2, LIST_WIDTH - 31 - amountWidth,
                ScreenStyle.TEXT);
        ScreenStyle.drawFitted(g, font, Component.translatable(line.state().translationKey()), x + 23, y + 11,
                LIST_WIDTH - 27, stateColor(line.state()));
    }

    private static int stateColor(EncoderState state) {
        return switch (state) {
            case READY -> ScreenStyle.SUCCESS;
            case IN_OUTPUT -> ScreenStyle.INFO;
            case NO_RECIPE -> ScreenStyle.WARNING;
            case EXCLUDED -> ScreenStyle.TEXT_MUTED;
        };
    }

    private static boolean isOverRow(double mouseX, double mouseY, int rowX, int rowY) {
        return mouseX >= rowX && mouseX < rowX + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + PatternEncoderMenu.ROW_HEIGHT;
    }

    /** Linha sob o mouse, ou null. */
    private @Nullable EncoderLine lineAt(double mouseX, double mouseY) {
        int x = leftPos + PADDING;
        int y = topPos + PatternEncoderMenu.LIST_Y;
        if (mouseX < x || mouseX >= x + LIST_WIDTH || mouseY < y) {
            return null;
        }
        int row = (int) (mouseY - y) / PatternEncoderMenu.ROW_HEIGHT;
        int index = firstRow + row;
        List<EncoderLine> lines = menu.getSnapshot().lines();
        return row < PatternEncoderMenu.VISIBLE_ROWS && index < lines.size() ? lines.get(index) : null;
    }

    /** O título já é desenhado no {@link #renderBg}. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        EncoderLine line = menu.getCarried().isEmpty() ? lineAt(mouseX, mouseY) : null;
        if (line == null) {
            renderTooltip(g, mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, line.result()));
        lines.add(Component.translatable("gui.tccolonybridge.encoder.requested", line.amount()).withStyle(ChatFormatting.AQUA));
        if (!line.inputs().isEmpty()) {
            lines.add(Component.translatable("gui.tccolonybridge.encoder.ingredients").withStyle(ChatFormatting.GRAY));
            for (ItemStack input : line.inputs()) {
                lines.add(Component.literal(" " + input.getCount() + "× ").append(input.getHoverName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (line.state() == EncoderState.READY) {
            lines.add(Component.translatable("gui.tccolonybridge.encoder.click").withStyle(ChatFormatting.GREEN));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        EncoderLine line = lineAt(mouseX, mouseY);
        if (line != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && menu.getCarried().isEmpty()) {
            if (line.state() == EncoderState.READY) {
                send(line.result());
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = leftPos + PADDING;
        int y = topPos + PatternEncoderMenu.LIST_Y;
        if (mouseX >= x && mouseX < x + LIST_WIDTH + 8 && mouseY >= y
                && mouseY < y + PatternEncoderMenu.VISIBLE_ROWS * PatternEncoderMenu.ROW_HEIGHT) {
            firstRow = Math.max(0, firstRow - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Áreas fora da janela (a barra lateral), para o JEI não desenhar por cima. */
    public List<Rect2i> extraAreas() {
        return List.of(toolbar.area());
    }
}
