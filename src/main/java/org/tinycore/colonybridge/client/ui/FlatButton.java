package org.tinycore.colonybridge.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Botão plano no estilo do mod (fundo escuro, borda de destaque no hover), em vez do botão
 * de pedra do Minecraft. O texto pode ser trocado a qualquer momento com {@code setMessage};
 * {@code setSelected} deixa a borda destacada (usado nas abas).
 */
public class FlatButton extends AbstractButton {

    private final Runnable onPress;
    /** Destaque fixo (ex.: aba ativa). */
    private boolean selected;

    public FlatButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = selected || isHoveredOrFocused() ? UiColors.ACCENT : UiColors.BORDER;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                isHoveredOrFocused() ? UiColors.PANEL_HOVER : UiColors.PANEL);
        renderFittedText(graphics);
    }

    /** Texto centralizado; se não couber (ex.: tradução longa), é reduzido em escala até caber. */
    private void renderFittedText(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int available = width - 6;
        int textWidth = font.width(getMessage());
        float scale = textWidth > available ? (float) available / textWidth : 1f;
        float centerX = getX() + width / 2f;
        float centerY = getY() + height / 2f;
        // pose = matriz de transformação do desenho; push/pop isola a escala só para este texto
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, getMessage(), -textWidth / 2, -font.lineHeight / 2 + 1, UiColors.TEXT, false);
        graphics.pose().popPose();
    }

    /** Leitores de tela: narra o texto do botão, como o botão padrão. */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
