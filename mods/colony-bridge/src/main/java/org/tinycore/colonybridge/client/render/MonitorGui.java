package org.tinycore.colonybridge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.tinycore.colonybridge.block.monitor.MonitorData;

import java.util.Objects;

/**
 * Desenha os painéis do Monitor da Colônia numa tela de interface (aba de painel do tablet), com o mesmo código
 * do monitor no mundo ({@link MonitorPanels}, {@link SupplyPanel}): só muda a matriz. O painel é montado num
 * tamanho "de monitor" (pixels de tela de um multibloco, que decide o layout) e escalado para caber na janela.
 * <p>
 * Guarda o estado de uma tela aberta: a animação dos números ({@link MonitorAnimator}) e a página das listas, que
 * troca sozinha a cada {@link #PAGE_MILLIS} ou com {@link #nextPage}/{@link #previousPage} (clique na tela).
 */
public final class MonitorGui {

    private static final long PAGE_MILLIS = 10_000;

    private final MonitorAnimator animator = new MonitorAnimator();
    private final int width;
    private final int height;
    private int page;
    private int pages = 1;
    private long pageSince = Util.getMillis();

    /**
     * @param width  largura lógica do painel (como um monitor de {@code width / 64} blocos)
     * @param height altura lógica
     */
    public MonitorGui(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** Desenha o painel centralizado na área {@code (x, y, guiWidth, guiHeight)} da janela. */
    public void render(GuiGraphics g, int x, int y, int guiWidth, int guiHeight, MonitorData data) {
        Minecraft minecraft = Minecraft.getInstance();
        float scale = Math.min((float) guiWidth / width, (float) guiHeight / height);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + (guiWidth - width * scale) / 2f, y + (guiHeight - height * scale) / 2f, 10);
        pose.scale(scale, scale, 1f);
        MonitorCanvas canvas = new MonitorCanvas(pose, g.bufferSource(), minecraft.font,
                minecraft.getItemRenderer(), Objects.requireNonNull(minecraft.level), false);
        animator.beginFrame();
        MonitorPanels.render(canvas, width, height, data, this::pageFor, animator);
        g.flush(); // desenha já, antes do resto da interface por cima
        pose.popPose();
    }

    /** Chamado pelo painel com o total de páginas; devolve a atual (troca sozinha com o tempo). */
    private int pageFor(int total) {
        pages = Math.max(1, total);
        if (Util.getMillis() - pageSince >= PAGE_MILLIS) {
            page++;
            pageSince = Util.getMillis();
        }
        page = Math.floorMod(page, pages);
        return page;
    }

    public void nextPage() {
        page = Math.floorMod(page + 1, pages);
        pageSince = Util.getMillis();
    }

    public void previousPage() {
        page = Math.floorMod(page - 1, pages);
        pageSince = Util.getMillis();
    }
}
