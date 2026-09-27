package org.tinycore.colonybridge.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Botão plano no estilo do mod (fundo escuro, borda de destaque no hover), em vez do botão
 * de pedra do Minecraft. O texto pode ser trocado a qualquer momento com {@code setMessage}.
 */
public class FlatButton extends AbstractButton {

    private final Runnable onPress;

    public FlatButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = isHoveredOrFocused() ? UiColors.ACCENT : UiColors.BORDER;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                isHoveredOrFocused() ? UiColors.PANEL_HOVER : UiColors.PANEL);
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                getY() + (height - font.lineHeight) / 2 + 1, UiColors.TEXT);
    }

    /** Leitores de tela: narra o texto do botão, como o botão padrão. */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
