package org.tinycore.colonybridge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.tinycore.colonybridge.block.monitor.MonitorBlockEntity;
import org.tinycore.colonybridge.multiblock.MonitorFormation;
import org.tinycore.core.client.ui.UiColors;

import java.util.HashMap;
import java.util.Map;

/**
 * Desenha a tela do monitor no mundo (um {@code BlockEntityRenderer}, chamado a cada frame para
 * block entities visíveis). Só o bloco <b>mestre</b> desenha, cobrindo a tela inteira.
 * <p>
 * Transformação: gira a matriz para a frente do monitor ficar no +Z local, vai até o canto superior
 * esquerdo da face e escala para "pixels de tela" ({@link #PIXELS_PER_BLOCK} por bloco, y para baixo),
 * como numa interface. Depois disso, quem desenha é o {@link MonitorCanvas}.
 * <p>
 * Nenhuma lógica de jogo aqui: só lê o que o block entity já tem.
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {

    /** Resolução da tela: pixels de desenho por bloco. */
    static final int PIXELS_PER_BLOCK = 64;
    /** Distância máxima (em blocos) em que a tela é desenhada. */
    private static final int VIEW_DISTANCE = 32;
    /** Afasta a tela da face do bloco para não brigar com a textura dele. */
    private static final float FACE_OFFSET = 0.002f;

    /** Tempo sem ser desenhada até uma tela perder o animador (saiu de vista ou foi quebrada). */
    private static final long ANIMATOR_TTL_MILLIS = 30_000;

    private final Font font;
    /** Animação por tela (só cliente): estado de exibição mantido entre frames. */
    private final Map<BlockPos, MonitorAnimator> animators = new HashMap<>();

    public MonitorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    /** Animador desta tela, criando se preciso e descartando os que ninguém está vendo. */
    private MonitorAnimator animatorFor(BlockPos pos) {
        MonitorAnimator animator = animators.computeIfAbsent(pos.immutable(), p -> new MonitorAnimator());
        animator.beginFrame();
        if (animators.size() > 1) {
            long cutoff = System.currentTimeMillis() - ANIMATOR_TTL_MILLIS;
            animators.values().removeIf(a -> a.lastUsedMillis < cutoff);
        }
        return animator;
    }

    @Override
    public void render(MonitorBlockEntity monitor, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!monitor.isMaster()) {
            return;
        }
        Direction facing = monitor.getFacing();
        if (isBehind(monitor.getBlockPos(), facing)) {
            return; // câmera atrás da tela: nada visível
        }
        int width = monitor.getWidth() * PIXELS_PER_BLOCK;
        int height = monitor.getHeight() * PIXELS_PER_BLOCK;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot())); // agora +Z = frente, +X = direita de quem olha
        pose.translate(-0.5, -0.5 + monitor.getHeight(), 0.5 + FACE_OFFSET); // canto superior esquerdo
        float scale = 1f / PIXELS_PER_BLOCK;
        pose.scale(scale, -scale, scale); // y para baixo, como numa interface

        MonitorCanvas canvas = new MonitorCanvas(pose, buffers, font,
                Minecraft.getInstance().getItemRenderer(), monitor.getLevel(), true);
        if (monitor.isValidStructure()) {
            MonitorPanels.render(canvas, width, height, monitor.getData(), monitor::currentPage,
                    animatorFor(monitor.getBlockPos()));
        } else {
            renderInvalid(canvas, width, height);
        }
        pose.popPose();
    }

    private static void renderInvalid(MonitorCanvas canvas, int width, int height) {
        canvas.fill(0, 0, width, height, UiColors.BACKGROUND | 0xFF000000, 0);
        canvas.fill(0, 0, width, 3, UiColors.DANGER, 1);
        canvas.textCentered(Component.translatable("monitor.tccolonybridge.invalid"), width / 2f,
                height / 2f - 4, UiColors.DANGER, 1f, width - 6, 2);
    }

    /** true se a câmera está atrás do plano da tela (não precisa desenhar). */
    static boolean isBehind(BlockPos pos, Direction facing) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = camera.x - (pos.getX() + 0.5);
        double dz = camera.z - (pos.getZ() + 0.5);
        return dx * facing.getStepX() + dz * facing.getStepZ() < 0.5;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /**
     * Área ocupada pela tela inteira. Sem isto, o Minecraft deixaria de desenhar a tela quando o bloco
     * mestre saísse do campo de visão, mesmo com o resto da tela ainda visível.
     */
    @Override
    public AABB getRenderBoundingBox(MonitorBlockEntity monitor) {
        BlockPos master = monitor.getBlockPos();
        BlockPos far = master.relative(MonitorFormation.right(monitor.getFacing()), monitor.getWidth() - 1)
                .above(monitor.getHeight() - 1);
        return AABB.encapsulatingFullBlocks(master, far);
    }
}
