package org.tinycore.colonybridge.client.supply;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.menu.supply.SupplySnapshot;
import org.tinycore.colonybridge.network.SupplyConfigPayload;
import org.tinycore.core.client.ui.IconButton;
import org.tinycore.core.client.ui.RedstoneIcons;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.SideToolbar;
import org.tinycore.core.client.ui.UiFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela do bloco de abastecimento (só cliente), com a estrutura dos terminais do AE2 e as cores da marca
 * ({@link ScreenStyle}): barra lateral com ajuda "?" e redstone, duas seções de slots (manter / excedente)
 * com quantidade alvo e o inventário embaixo.
 * <p>
 * O item de cada linha é escolhido clicando no slot (o menu cuida disso). A <b>quantidade</b> muda com a
 * roda do mouse sobre o slot — Shift multiplica por 10, Ctrl por 64. Cada mudança manda a configuração
 * inteira ao servidor, que valida e responde com o estado novo.
 */
public class ColonySupplyScreen extends AbstractContainerScreen<ColonySupplyMenu> {

    private static final int WIDTH = 182;
    private static final int HEIGHT = 194;
    private static final int PADDING = 8;

    private SideToolbar toolbar;
    private IconButton redstoneButton;
    /** Estado local: muda na hora do clique e é corrigido pelo próximo snapshot do servidor. */
    private SupplySnapshot settings = SupplySnapshot.EMPTY;
    private SupplySnapshot lastSeen;

    public ColonySupplyScreen(ColonySupplyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        toolbar = new SideToolbar(SideToolbar.Side.LEFT);
        IconButton help = addRenderableWidget(toolbar.add(new IconButton(0, 0, () -> {}).glyph("?")));
        help.setTooltipText(Component.translatable("gui.tccolonybridge.help.supply"));
        redstoneButton = addRenderableWidget(toolbar.add(new IconButton(0, 0, this::cycleRedstone)));
        toolbar.layout(leftPos, topPos, WIDTH);
        lastSeen = null; // força copiar o snapshot atual
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        SupplySnapshot snapshot = menu.getSnapshot();
        if (snapshot != lastSeen) {
            lastSeen = snapshot;
            settings = snapshot;
        }
        redstoneButton.icon(RedstoneIcons.of(settings.redstoneMode()));
        redstoneButton.setTooltipText(Component.translatable("gui.tccolonybridge.redstone",
                Component.translatable(settings.redstoneMode().translationKey())));
    }

    private void cycleRedstone() {
        settings = settings.withRedstone(settings.redstoneMode().next());
        send();
    }

    private void send() {
        PacketDistributor.sendToServer(new SupplyConfigPayload(menu.containerId,
                settings.redstoneMode().ordinal(), settings.amounts()));
    }

    // ---------------------------------------------------------------- desenho

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        SupplySnapshot snap = menu.getSnapshot();
        int x = leftPos;
        int y = topPos;
        int inner = WIDTH - PADDING * 2;
        toolbar.render(g);
        ScreenStyle.window(g, x, y, WIDTH, HEIGHT);

        // Estado à direita primeiro: o título usa o espaço que sobrar (corta com "…" se preciso).
        Component status = Component.translatable(snap.status().guiKey());
        int statusWidth = ScreenStyle.drawFittedRight(g, font, status, x + WIDTH - PADDING, y + 7, inner / 2,
                ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + WIDTH - PADDING - statusWidth - 10, y + 7, StatusColors.of(snap.status()));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, y + 7, inner - statusWidth - 16, ScreenStyle.TITLE);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony")
                : Component.literal(snap.colonyName());
        ScreenStyle.drawFitted(g, font, colony, x + PADDING, y + 19, inner, ScreenStyle.INFO);

        section(g, x, y + ColonySupplyMenu.KEEP_Y - 11, Component.translatable("gui.tccolonybridge.supply.keep"));
        section(g, x, y + ColonySupplyMenu.SURPLUS_Y - 11, Component.translatable("gui.tccolonybridge.supply.surplus"));
        section(g, x, y + ColonySupplyMenu.INVENTORY_Y - 11, Component.translatable("gui.tccolonybridge.inventory"));
        for (Slot slot : menu.slots) {
            ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
        }
    }

    /** Título de seção, como no terminal do AE2. */
    private void section(GuiGraphics g, int x, int y, Component label) {
        ScreenStyle.drawFitted(g, font, label, x + PADDING, y, WIDTH - PADDING * 2, ScreenStyle.TEXT);
    }

    /**
     * Quantidade alvo sobre cada linha configurada. Fica aqui, e não no {@code renderBg}, porque este método
     * roda <b>depois</b> que os itens são desenhados (senão o número fica escondido atrás do ícone).
     * As coordenadas já são relativas ao canto da janela.
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < StockList.SIZE && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.getItem().isEmpty()) {
                continue;
            }
            String amount = UiFormat.compact(settings.amount(i));
            g.pose().pushPose();
            // z 300: acima do item e da contagem padrão de itens (z 200)
            g.pose().translate(slot.x + 17f - font.width(amount) * 0.6f, slot.y + 11f, 300);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, amount, 0, 0, 0xFFFFFFFF, true); // branco com sombra, como a contagem de itens
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** Áreas fora da janela ocupadas pela tela (a barra lateral), para o JEI não desenhar por cima. */
    public List<Rect2i> extraAreas() {
        return List.of(toolbar.area());
    }

    /**
     * Tooltip do item sob o cursor. Numa linha configurada, acrescenta ao tooltip normal do item a quantidade
     * alvo, quanto existe hoje e como mudar — tudo num tooltip só (dois tooltips no mesmo lugar ficavam
     * um por cima do outro). {@code hoveredSlot} é o slot sob o mouse, preenchido pela própria tela.
     */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot == null || hoveredSlot.index >= StockList.SIZE) {
            return lines;
        }
        int index = hoveredSlot.index;
        List<Component> extended = new ArrayList<>(lines);
        extended.add(Component.translatable("gui.tccolonybridge.supply.target", settings.amount(index))
                .withStyle(ChatFormatting.GOLD));
        extended.add(Component.translatable("gui.tccolonybridge.supply.current", menu.getSnapshot().count(index))
                .withStyle(ChatFormatting.AQUA));
        extended.add(Component.translatable("gui.tccolonybridge.supply.scroll").withStyle(ChatFormatting.GRAY));
        return extended;
    }

    /** Roda do mouse sobre uma linha muda a quantidade alvo (Shift ×10, Ctrl ×64). */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int index = stockSlotAt((int) mouseX, (int) mouseY);
        if (index < 0 || menu.slots.get(index).getItem().isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int step = hasControlDown() ? 64 : hasShiftDown() ? 10 : 1;
        int amount = settings.amount(index) + (int) Math.signum(scrollY) * step;
        settings = settings.withAmount(index, Math.max(0, Math.min(StockList.MAX_AMOUNT, amount)));
        send();
        return true;
    }

    /** Índice da linha de estoque sob o cursor, ou -1. */
    private int stockSlotAt(int mouseX, int mouseY) {
        for (int i = 0; i < StockList.SIZE && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                    && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                return i;
            }
        }
        return -1;
    }
}
