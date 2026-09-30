package org.tinycore.colonybridge.client.loader;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.client.tablet.TabletTabBar;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.logic.loader.LoaderState;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderMenu;
import org.tinycore.colonybridge.menu.loader.ChunkLoaderSnapshot;
import org.tinycore.colonybridge.network.ChunkLoaderActionPayload;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.RedstoneIcons;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;
import org.tinycore.core.client.ui.UiFormat;

import java.util.List;

/**
 * Tela do Chunk Loader (só cliente; no bloco ou pela aba do tablet), com a estrutura das outras telas do mod e as
 * cores da marca: barra lateral com ajuda "?", liga/desliga e redstone; no corpo, a situação em destaque e três
 * cartões — chunks carregados, consumo de energia e tempo até soltar a área.
 */
public class ChunkLoaderScreen extends AbstractContainerScreen<ChunkLoaderMenu> {

    private static final int WIDTH = 202;
    private static final int HEIGHT = 128;
    private static final int PADDING = 8;
    private static final int STATE_Y = 34;
    private static final int CARDS_Y = 58;
    private static final int CARD_HEIGHT = 28;

    private SideToolbar toolbar;
    private IconButton powerButton;
    private IconButton redstoneButton;

    public ChunkLoaderScreen(ChunkLoaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos += TabletTabBar.offset(menu.tabletView());
        TabletTabBar.add(menu.tabletView(), this::addRenderableWidget, leftPos, topPos);
        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        IconButton help = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> {}).glyph("?")));
        help.setTooltipText(Component.translatable("gui.tccolonybridge.help.loader"));
        powerButton = addRenderableWidget(toolbar.add(new IconButton(0, 0,
                () -> send(ChunkLoaderActionPayload.TOGGLE)).icon(new ItemStack(Items.LEVER))));
        redstoneButton = addRenderableWidget(toolbar.add(new IconButton(0, 0,
                () -> send(ChunkLoaderActionPayload.REDSTONE))));
        toolbar.layout(leftPos, topPos, WIDTH);
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new ChunkLoaderActionPayload(menu.containerId, action));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ChunkLoaderSnapshot snap = menu.getSnapshot();
        powerButton.setBadge(snap.switchedOn() ? ScreenStyle.SUCCESS : ScreenStyle.DANGER);
        powerButton.setTooltipText(Component.translatable("gui.tccolonybridge.loader.power",
                Component.translatable(snap.switchedOn() ? "gui.tccolonybridge.on" : "gui.tccolonybridge.off")));
        redstoneButton.icon(RedstoneIcons.of(snap.redstoneMode()));
        redstoneButton.setTooltipText(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(snap.redstoneMode().translationKey())));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        ChunkLoaderSnapshot snap = menu.getSnapshot();
        int x = leftPos;
        int y = topPos;
        int inner = WIDTH - PADDING * 2;
        toolbar.render(g);
        ScreenStyle.window(g, x, y, WIDTH, HEIGHT);

        Component status = Component.translatable(snap.status().guiKey());
        int statusWidth = ScreenStyle.drawFittedRight(g, font, status, x + WIDTH - PADDING, y + 7, inner / 2,
                ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + WIDTH - PADDING - statusWidth - 10, y + 7, StatusColors.of(snap.status()));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, y + 7, inner - statusWidth - 16, ScreenStyle.TITLE);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony")
                : Component.literal(snap.colonyName());
        ScreenStyle.drawFitted(g, font, colony, x + PADDING, y + 19, inner, ScreenStyle.INFO);

        ScreenStyle.inset(g, x + PADDING, y + STATE_Y, inner, 18, ScreenStyle.PANEL);
        ScreenStyle.statusDot(g, x + PADDING + 6, y + STATE_Y + 5, stateColor(snap.state()));
        ScreenStyle.drawFitted(g, font, Component.translatable(snap.state().translationKey()), x + PADDING + 16,
                y + STATE_Y + 5, inner - 20, ScreenStyle.TEXT);

        int cardWidth = (inner - 8) / 3;
        card(g, x + PADDING, "gui.tccolonybridge.loader.chunks",
                String.valueOf(snap.loaded()), ScreenStyle.TEXT, cardWidth);
        card(g, x + PADDING + cardWidth + 4, "gui.tccolonybridge.loader.energy",
                UiFormat.compact(Math.round(snap.power())) + " AE/t", ScreenStyle.INFO, cardWidth);
        card(g, x + PADDING + (cardWidth + 4) * 2, "gui.tccolonybridge.loader.timer", timer(snap),
                snap.state() == LoaderState.COUNTDOWN ? ScreenStyle.WARNING : ScreenStyle.TEXT_MUTED, cardWidth);
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.loader.claimed", snap.claimed(),
                snap.max()), x + PADDING, y + CARDS_Y + CARD_HEIGHT + 8, inner, ScreenStyle.TEXT_MUTED);
    }

    private void card(GuiGraphics g, int x, String labelKey, String value, int color, int width) {
        int y = topPos + CARDS_Y;
        ScreenStyle.inset(g, x, y, width, CARD_HEIGHT, ScreenStyle.PANEL);
        ScreenStyle.drawFitted(g, font, Component.translatable(labelKey), x + 4, y + 4, width - 8, ScreenStyle.TEXT_MUTED);
        ScreenStyle.drawFitted(g, font, Component.literal(value), x + 4, y + 16, width - 8, color);
    }

    /** "3 h 20 min" durante a contagem; "—" fora dela. */
    private static String timer(ChunkLoaderSnapshot snap) {
        if (snap.state() != LoaderState.COUNTDOWN) {
            return "—";
        }
        long hours = snap.minutesLeft() / 60;
        long minutes = snap.minutesLeft() % 60;
        return hours > 0 ? hours + " h " + minutes + " min" : Math.max(1, minutes) + " min";
    }

    private static int stateColor(LoaderState state) {
        return switch (state) {
            case LOADING -> ScreenStyle.SUCCESS;
            case COUNTDOWN -> ScreenStyle.WARNING;
            case NO_POWER, NO_COLONY, DISABLED_BY_ADMIN -> ScreenStyle.DANGER;
            case OFF, SLEEPING -> ScreenStyle.TEXT_MUTED;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Áreas fora da janela (a barra lateral), para o JEI não desenhar por cima. */
    public List<Rect2i> extraAreas() {
        return List.of(toolbar.area());
    }
}
