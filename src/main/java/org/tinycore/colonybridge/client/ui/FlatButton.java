package org.tinycore.colonybridge.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Botão das telas do mod no visual dos terminais do AE2 ({@link ScreenStyle}), em vez do botão de pedra
 * do Minecraft. O texto pode ser trocado a qualquer momento com {@code setMessage};
 * {@code setSelected} marca a opção/aba ativa.
 */
public class FlatButton extends AbstractButton {

    /** Menor escala aceita para o texto antes de cortar com "…" (abaixo disso fica ilegível). */
    private static final float MIN_SCALE = 0.75f;

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

    /**
     * Botão no estilo AE2: contorno escuro, fio claro em cima e fundo cinza. Aba/opção selecionada fica
     * com a cor da janela (parece "puxada" para frente); com o mouse em cima, um tom mais claro.
     */
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int fill = selected ? ScreenStyle.WINDOW : isHoveredOrFocused() ? ScreenStyle.SLOT : ScreenStyle.SHADE;
        int x = getX();
        int y = getY();
        graphics.fill(x, y, x + width, y + height, ScreenStyle.FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, ScreenStyle.LIGHT);
        graphics.fill(x + 1, y + 2, x + width - 1, y + height - 1, fill);
        renderFittedText(graphics);
    }

    /**
     * Texto centralizado. Se não couber (tradução longa, fonte larga do modpack), reduz a escala até
     * {@link #MIN_SCALE}; se ainda assim não couber, corta com "…".
     */
    private void renderFittedText(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int available = width - 6;
        int textWidth = font.width(getMessage());
        float scale = textWidth > available ? Math.max(MIN_SCALE, (float) available / textWidth) : 1f;
        FormattedCharSequence text = ScreenStyle.fit(font, getMessage(), (int) (available / scale));
        int drawnWidth = font.width(text);
        float centerX = getX() + width / 2f;
        float centerY = getY() + height / 2f + 1;
        // pose = matriz de transformação do desenho; push/pop isola a escala só para este texto
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, -drawnWidth / 2, -font.lineHeight / 2 + 1, ScreenStyle.TEXT_ON_BUTTON, false);
        graphics.pose().popPose();
    }

    /** Leitores de tela: narra o texto do botão, como o botão padrão. */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
