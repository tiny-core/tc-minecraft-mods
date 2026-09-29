package org.tinycore.core.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Botão quadrado com ícone, como os da barra lateral e as abas dos terminais do AE2: o ícone é um item
 * do jogo (ou um caractere, ex.: "?"), e o texto aparece só no tooltip. Assim nenhum texto estoura o
 * botão, qualquer que seja a fonte ou o idioma.
 * <p>
 * Um "selo" colorido no canto ({@link #setBadge}) mostra estado sem texto (ex.: verde = ligado).
 */
public class IconButton extends AbstractButton {

    public static final int SIZE = 18;

    private final Runnable onPress;
    private ItemStack icon = ItemStack.EMPTY;
    private String glyph = "";
    private boolean selected;
    /** Cor do selo no canto inferior direito; 0 = sem selo. */
    private int badge;
    private Component tooltipText = Component.empty();

    public IconButton(int x, int y, Runnable onPress) {
        super(x, y, SIZE, SIZE, Component.empty());
        this.onPress = onPress;
    }

    public IconButton icon(ItemStack value) {
        this.icon = value;
        this.glyph = "";
        return this;
    }

    public IconButton glyph(String value) {
        this.glyph = value;
        this.icon = ItemStack.EMPTY;
        return this;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setBadge(int color) {
        this.badge = color;
    }

    /** Texto do tooltip (e da narração). Só recria o tooltip quando o texto muda. */
    public void setTooltipText(Component text) {
        if (Objects.equals(text, tooltipText)) {
            return;
        }
        tooltipText = text;
        setMessage(text);
        setTooltip(Tooltip.create(text));
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int border = selected ? ScreenStyle.ACCENT : isHoveredOrFocused() ? ScreenStyle.INFO : ScreenStyle.FRAME;
        g.fill(x, y, x + width, y + height, border);
        g.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                selected || isHoveredOrFocused() ? ScreenStyle.HOVER : ScreenStyle.PANEL);
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 1, y + 1);
        } else if (!glyph.isEmpty()) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, glyph, x + (width - font.width(glyph)) / 2 + 1, y + (height - 8) / 2 + 1,
                    ScreenStyle.ACCENT, false);
        }
        if (badge != 0) {
            // selo acima do ícone do item (itens são desenhados em z ~150)
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + width - 7, y + height - 7, x + width - 1, y + height - 1, ScreenStyle.FRAME);
            g.fill(x + width - 6, y + height - 6, x + width - 2, y + height - 2, badge);
            g.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
