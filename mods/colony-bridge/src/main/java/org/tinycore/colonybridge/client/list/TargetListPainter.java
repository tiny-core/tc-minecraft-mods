package org.tinycore.colonybridge.client.list;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.target.TargetKind;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.logic.target.TargetSpec;
import org.tinycore.colonybridge.menu.TargetLineView;
import org.tinycore.core.client.ui.ScreenStyle;

import java.util.ArrayList;
import java.util.List;

import static org.tinycore.colonybridge.client.list.TargetRowLayout.AMOUNT_WIDTH;
import static org.tinycore.colonybridge.client.list.TargetRowLayout.BUTTON;
import static org.tinycore.colonybridge.client.list.TargetRowLayout.ROW_HEIGHT;
import static org.tinycore.colonybridge.client.list.TargetRowLayout.in;

/**
 * Desenho e dicas da lista de linhas ({@link TargetListWidget}), só cliente. Separado do widget para ele ficar só
 * com o estado (rolagem, rascunho) e a entrada; aqui nada muda de estado: tudo chega por parâmetro a cada frame.
 * <p>
 * O rascunho (linha aberta pelo "+", ainda sem alvo) é sempre a última, logo depois das linhas do servidor.
 */
final class TargetListPainter {

    private final Font font;
    private final TargetListKind kind;
    private final @Nullable TargetListWidget.LineInfo info;
    private final int rows;

    TargetListPainter(Font font, TargetListKind kind, @Nullable TargetListWidget.LineInfo info, int rows) {
        this.font = font;
        this.kind = kind;
        this.info = info;
        this.rows = rows;
    }

    /** Fundo, ícones e botões (as caixas de texto são desenhadas pela tela, por cima). */
    void render(GuiGraphics g, TargetRowLayout layout, List<TargetLineView> lines, int firstRow, boolean draft,
                int mouseX, int mouseY) {
        int total = lines.size() + (draft ? 1 : 0);
        renderHeader(g, layout, lines.size(), mouseX, mouseY);
        ScreenStyle.inset(g, layout.x(), layout.y() - 1, layout.scrollbarX() - layout.x() - 1, layout.height() + 2,
                ScreenStyle.PANEL);
        for (int row = 0; row < rows; row++) {
            int index = firstRow + row;
            if (index < lines.size()) {
                renderLine(g, layout, row, index, lines.get(index), mouseX, mouseY);
            } else if (draft && index == lines.size()) {
                renderDraft(g, layout, row, mouseX, mouseY);
            }
        }
        if (total == 0) {
            g.drawWordWrap(font, Component.translatable("gui.tccolonybridge.list.empty"), layout.x() + 6,
                    layout.y() + 6, layout.scrollbarX() - layout.x() - 12, ScreenStyle.TEXT_MUTED);
        }
        ScreenStyle.scrollbar(g, layout.scrollbarX(), layout.y() - 1, layout.height() + 2, firstRow, rows, total);
    }

    private void renderHeader(GuiGraphics g, TargetRowLayout layout, int count, int mouseX, int mouseY) {
        String counter = count + "/" + TargetListWidget.maxLines();
        g.drawString(font, counter, layout.addX() - 4 - font.width(counter), layout.addY() + 2, ScreenStyle.TEXT_MUTED,
                false);
        button(g, layout.addX(), layout.addY(), "+", ScreenStyle.ACCENT, mouseX, mouseY);
    }

    private void renderLine(GuiGraphics g, TargetRowLayout layout, int row, int index, TargetLineView line,
                            int mouseX, int mouseY) {
        int y = layout.rowY(row);
        TargetSpec spec = line.spec();
        if (info != null) {
            g.fill(layout.x() + 1, y + 2, layout.x() + 3, y + ROW_HEIGHT - 2, info.color(index));
        }
        ScreenStyle.slot(g, layout.iconX(), y + 1);
        g.renderItem(TargetIcons.icon(spec, line.item()), layout.iconX() + 1, y + 2);
        if (!line.item().isEmpty() && !line.item().getComponentsPatch().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200); // acima do item
            g.drawString(font, "✦", layout.iconX() + 12, y + 1, ScreenStyle.ACCENT, true);
            g.pose().popPose();
        }
        ScreenStyle.inset(g, layout.textX(), y + 3, layout.textWidth(), 14, ScreenStyle.SLOT);
        if (kind.hasAmount()) {
            ScreenStyle.inset(g, layout.amountX(), y + 3, AMOUNT_WIDTH, 14, ScreenStyle.SLOT);
        }
        if (kind.allowsAll()) {
            button(g, layout.allX(), y + 4, "∞", line.all() ? ScreenStyle.ACCENT : ScreenStyle.TEXT_MUTED, mouseX, mouseY);
        }
        button(g, layout.removeX(), y + 4, "x", ScreenStyle.DANGER, mouseX, mouseY);
    }

    private void renderDraft(GuiGraphics g, TargetRowLayout layout, int row, int mouseX, int mouseY) {
        int y = layout.rowY(row);
        ScreenStyle.slot(g, layout.iconX(), y + 1);
        g.drawCenteredString(font, "+", layout.iconX() + 9, y + 6, ScreenStyle.TEXT_MUTED);
        ScreenStyle.inset(g, layout.textX(), y + 3, layout.textWidth(), 14, ScreenStyle.SLOT);
        button(g, layout.removeX(), y + 4, "x", ScreenStyle.DANGER, mouseX, mouseY);
    }

    /** Botão pequeno desenhado à mão (não é widget: aparece e some com a linha, sem gerenciar dezenas de botões). */
    private void button(GuiGraphics g, int x, int y, String glyph, int color, int mouseX, int mouseY) {
        boolean hover = in(mouseX, mouseY, x, y, BUTTON, BUTTON);
        ScreenStyle.inset(g, x, y, BUTTON, BUTTON, hover ? ScreenStyle.HOVER : ScreenStyle.PANEL);
        g.drawCenteredString(font, glyph, x + BUTTON / 2, y + 2, color);
    }

    // ---------------------------------------------------------------- dicas

    /** Dica sob o cursor, ou null. {@code editor} responde pelas caixas de texto (alvo inválido). */
    @Nullable List<Component> tooltip(TargetRowLayout layout, List<TargetLineView> lines, int firstRow, boolean draft,
                                      TargetRowEditor editor, double mouseX, double mouseY) {
        if (in(mouseX, mouseY, layout.addX(), layout.addY(), BUTTON, BUTTON)) {
            return List.of(Component.translatable("gui.tccolonybridge.list.add"),
                    Component.translatable("gui.tccolonybridge.list.syntax").withStyle(ChatFormatting.GRAY));
        }
        int row = layout.rowAt(mouseX, mouseY);
        int index = firstRow + row;
        if (row < 0 || index >= lines.size() + (draft ? 1 : 0)) {
            return null;
        }
        int y = layout.rowY(row);
        if (in(mouseX, mouseY, layout.removeX(), y + 4, BUTTON, BUTTON)) {
            return List.of(Component.translatable("gui.tccolonybridge.list.remove"));
        }
        if (index == lines.size()) { // rascunho
            return in(mouseX, mouseY, layout.iconX(), y + 1, 18, 18)
                    ? List.of(Component.translatable("gui.tccolonybridge.list.draft")) : editor.problemAt(mouseX, mouseY);
        }
        TargetLineView line = lines.get(index);
        if (kind.allowsAll() && in(mouseX, mouseY, layout.allX(), y + 4, BUTTON, BUTTON)) {
            return List.of(Component.translatable(line.all() ? "gui.tccolonybridge.list.all_on" : "gui.tccolonybridge.list.all_off"));
        }
        if (in(mouseX, mouseY, layout.iconX(), y + 1, 18, 18)) {
            return iconTooltip(index, line);
        }
        return editor.problemAt(mouseX, mouseY);
    }

    private List<Component> iconTooltip(int index, TargetLineView line) {
        TargetSpec spec = line.spec();
        List<Component> lines = new ArrayList<>();
        lines.add(TargetIcons.name(spec, line.item()));
        if (spec != null && spec.kind() != TargetKind.ITEM) {
            lines.add(Component.translatable("gui.tccolonybridge.list.items", TargetIcons.itemCount(spec))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!line.item().isEmpty() && !line.item().getComponentsPatch().isEmpty()) {
            lines.add(Component.translatable("gui.tccolonybridge.list.components").withStyle(ChatFormatting.GOLD));
        }
        if (info != null) {
            info.appendTooltip(index, lines);
        }
        lines.add(Component.translatable("gui.tccolonybridge.list.icon_hint").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
}
