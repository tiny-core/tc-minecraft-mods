package org.tinycore.colonybridge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * "Pincel" para desenhar na tela do monitor, no mundo 3D — o equivalente do {@code GuiGraphics}
 * das telas de interface. Trabalha em "pixels de tela" (x para a direita, y para baixo, origem no
 * canto superior esquerdo); o {@link MonitorRenderer} já posicionou e escalou a matriz.
 * <p>
 * Tudo com luz máxima ({@code FULL_BRIGHT}) para parecer um display aceso. Retângulos usam o
 * {@code RenderType.textBackground()}, o mesmo do fundo das plaquinhas de nome — um tipo simples e
 * bem suportado por mods de shader.
 * <p>
 * {@code layer}: camadas sobrepostas ficam levemente à frente umas das outras para não "piscarem"
 * (z-fighting, quando duas superfícies no mesmo lugar brigam para aparecer).
 */
final class MonitorCanvas {

    private static final float LAYER_STEP = 0.4f;
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private final PoseStack pose;
    private final MultiBufferSource buffers;
    private final Font font;

    MonitorCanvas(PoseStack pose, MultiBufferSource buffers, Font font) {
        this.pose = pose;
        this.buffers = buffers;
        this.font = font;
    }

    /** Retângulo preenchido (cor ARGB, {@code 0xAARRGGBB}). Mesma ordem de vértices do GuiGraphics.fill. */
    void fill(float x0, float y0, float x1, float y1, int color, int layer) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.textBackground());
        float z = layer * LAYER_STEP;
        consumer.addVertex(matrix, x0, y0, z).setColor(color).setLight(LIGHT);
        consumer.addVertex(matrix, x0, y1, z).setColor(color).setLight(LIGHT);
        consumer.addVertex(matrix, x1, y1, z).setColor(color).setLight(LIGHT);
        consumer.addVertex(matrix, x1, y0, z).setColor(color).setLight(LIGHT);
    }

    /** Texto com escala; {@code (x, y)} é o canto superior esquerdo. */
    void text(Component text, float x, float y, int color, float scale, int layer) {
        pose.pushPose();
        pose.translate(x, y, layer * LAYER_STEP);
        pose.scale(scale, scale, 1f);
        font.drawInBatch(text, 0, 0, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LIGHT);
        pose.popPose();
    }

    /**
     * Texto centralizado em {@code centerX}, na escala {@code preferredScale} ou menor se não couber
     * em {@code maxWidth}. @return a altura usada (para empilhar linhas)
     */
    float textCentered(Component text, float centerX, float y, int color, float preferredScale, float maxWidth,
                       int layer) {
        float width = font.width(text);
        float scale = Math.min(preferredScale, maxWidth / Math.max(1, width));
        text(text, centerX - width * scale / 2f, y, color, scale, layer);
        return font.lineHeight * scale;
    }
}
