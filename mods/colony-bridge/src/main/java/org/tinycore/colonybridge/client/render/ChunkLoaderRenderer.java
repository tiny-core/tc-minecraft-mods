package org.tinycore.colonybridge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.tinycore.colonybridge.block.loader.ColonyChunkLoaderBlockEntity;
import org.tinycore.colonybridge.logic.loader.LoaderDisplay;
import org.tinycore.colonybridge.logic.loader.LoaderState;
import org.tinycore.core.client.ui.UiColors;
import org.tinycore.core.client.ui.UiFormat;

/**
 * Mini-display na face da frente do Chunk Loader (no mundo): situação em cor, número de chunks carregados,
 * consumo em AE/t e, na contagem, o tempo até soltar a área. Mesmo "pincel" do Monitor da Colônia
 * ({@link MonitorCanvas}, luz máxima), num quadrado de {@link #SIZE} pixels de tela com moldura.
 * <p>
 * Só desenha com o jogador a até {@link #VIEW_DISTANCE} blocos e na frente do bloco. Os dados vêm do resumo
 * sincronizado pelo block entity ({@link LoaderDisplay}), sem lógica de jogo aqui.
 */
public class ChunkLoaderRenderer implements BlockEntityRenderer<ColonyChunkLoaderBlockEntity> {

    private static final int SIZE = MonitorRenderer.PIXELS_PER_BLOCK;
    private static final int BEZEL = 7;
    private static final int VIEW_DISTANCE = 24;
    private static final float FACE_OFFSET = 0.002f;

    private final Font font;

    public ChunkLoaderRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ColonyChunkLoaderBlockEntity loader, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Direction facing = loader.getFacing();
        if (MonitorRenderer.isBehind(loader.getBlockPos(), facing) || loader.getLevel() == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot())); // +Z = frente do bloco
        pose.translate(-0.5, 0.5, 0.5 + FACE_OFFSET); // canto superior esquerdo da face
        float scale = 1f / SIZE;
        pose.scale(scale, -scale, scale); // y para baixo, como numa interface
        MonitorCanvas c = new MonitorCanvas(pose, buffers, font, Minecraft.getInstance().getItemRenderer(),
                loader.getLevel(), true);
        draw(c, loader.getDisplay());
        pose.popPose();
    }

    private static void draw(MonitorCanvas c, LoaderDisplay display) {
        int color = colorOf(display.state());
        float x0 = BEZEL;
        float x1 = SIZE - BEZEL;
        float center = SIZE / 2f;
        float width = x1 - x0 - 4;
        c.fill(x0, BEZEL, x1, SIZE - BEZEL, UiColors.BACKGROUND | 0xFF000000, 0);
        c.fill(x0, BEZEL, x1, BEZEL + 2, color, 1);
        c.textCentered(Component.translatable("gui.tccolonybridge.loader.face.title"), center, BEZEL + 4,
                UiColors.TEXT_MUTED, 0.4f, width, 2);
        c.textCentered(Component.literal(String.valueOf(display.loaded())), center, BEZEL + 11, color, 1.3f, width, 2);
        c.textCentered(Component.translatable("gui.tccolonybridge.loader.face.chunks"), center, BEZEL + 23,
                UiColors.TEXT_MUTED, 0.4f, width, 2);
        c.textCentered(bottomLine(display), center, BEZEL + 31, display.state() == LoaderState.COUNTDOWN
                ? UiColors.WARNING : UiColors.TEXT, 0.4f, width, 2);
        c.textCentered(Component.literal(UiFormat.compact(display.power()) + " AE/t"), center, BEZEL + 39,
                UiColors.HIGHLIGHT, 0.4f, width, 2);
    }

    /** Na contagem, o tempo até soltar; fora dela, a situação em uma palavra ou duas. */
    private static Component bottomLine(LoaderDisplay display) {
        if (display.state() != LoaderState.COUNTDOWN) {
            return Component.translatable("gui.tccolonybridge.loader.face." + display.state().name().toLowerCase());
        }
        long hours = display.minutesLeft() / 60;
        long minutes = display.minutesLeft() % 60;
        return Component.literal(hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + " min");
    }

    private static int colorOf(LoaderState state) {
        return switch (state) {
            case LOADING -> UiColors.SUCCESS;
            case COUNTDOWN -> UiColors.WARNING;
            case NO_POWER, NO_COLONY, DISABLED_BY_ADMIN -> UiColors.DANGER;
            case OFF, SLEEPING -> UiColors.TEXT_MUTED;
        };
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
