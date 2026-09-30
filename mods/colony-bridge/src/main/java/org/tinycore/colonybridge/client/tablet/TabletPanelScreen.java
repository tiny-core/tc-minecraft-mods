package org.tinycore.colonybridge.client.tablet;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.tinycore.colonybridge.client.render.MonitorGui;
import org.tinycore.colonybridge.menu.tablet.TabletPanelMenu;
import org.tinycore.core.client.ui.ScreenStyle;

/**
 * Tela da aba de painel do tablet (só cliente): o painel do Monitor da Colônia de um bloco (Ponte ou Abastecedor),
 * desenhado pelo mesmo código do monitor ({@link MonitorGui}) num tamanho de 5 × 3 blocos, escalado para a janela.
 * <p>
 * As listas trocam de página sozinhas a cada 10 s; clique na metade direita do painel avança e na esquerda volta,
 * como no monitor.
 */
public class TabletPanelScreen extends AbstractContainerScreen<TabletPanelMenu> {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 170;
    private static final int PADDING = 8;
    private static final int PANEL_Y = 20;
    /** Tamanho lógico do painel: um monitor de 5 × 3 blocos (64 pixels de tela por bloco). */
    private static final int PANEL_WIDTH = 5 * 64;
    private static final int PANEL_HEIGHT = 3 * 64;

    private final MonitorGui panel = new MonitorGui(PANEL_WIDTH, PANEL_HEIGHT);

    public TabletPanelScreen(TabletPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos += TabletTabBar.offset(menu.tabletView());
        TabletTabBar.add(menu.tabletView(), this::addRenderableWidget, leftPos, topPos);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ScreenStyle.window(g, leftPos, topPos, WIDTH, HEIGHT);
        ScreenStyle.drawFitted(g, font, title, leftPos + PADDING, topPos + 7, WIDTH - PADDING * 2, ScreenStyle.TITLE);
        panel.render(g, leftPos + PADDING, topPos + PANEL_Y, panelWidth(), panelHeight(), menu.getData());
    }

    private int panelWidth() {
        return WIDTH - PADDING * 2;
    }

    private int panelHeight() {
        return HEIGHT - PANEL_Y - PADDING;
    }

    /** Título já desenhado em renderBg; sem inventário. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Clique no painel troca a página das listas: metade direita avança, esquerda volta. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double x = mouseX - leftPos - PADDING;
        double y = mouseY - topPos - PANEL_Y;
        if (x >= 0 && x < panelWidth() && y >= 0 && y < panelHeight()) {
            if (x >= panelWidth() / 2.0) {
                panel.nextPage();
            } else {
                panel.previousPage();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
