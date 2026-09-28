package org.tinycore.colonybridge.client.supply;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.client.ui.FlatButton;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;
import org.tinycore.colonybridge.client.ui.UiFormat;
import org.tinycore.colonybridge.menu.supply.ColonySupplyMenu;
import org.tinycore.colonybridge.menu.supply.SupplySnapshot;
import org.tinycore.colonybridge.network.SupplyConfigPayload;

import java.util.List;

/**
 * Tela do bloco de abastecimento (só cliente): duas listas de itens com quantidade alvo e um botão de
 * redstone.
 * <p>
 * O item de cada linha é escolhido clicando no slot (o menu cuida disso). A <b>quantidade</b> muda com a
 * roda do mouse sobre o slot — Shift multiplica por 10, Ctrl por 64. Cada mudança manda a configuração
 * inteira ao servidor, que valida e responde com o estado novo.
 */
public class ColonySupplyScreen extends AbstractContainerScreen<ColonySupplyMenu> {

    private static final int WIDTH = 236;
    private static final int HEIGHT = 204;
    private static final int PADDING = 8;

    private FlatButton redstoneButton;
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
        redstoneButton = addRenderableWidget(new FlatButton(leftPos + WIDTH - PADDING - 90, topPos + 20, 90, 14,
                Component.empty(), this::cycleRedstone));
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
        redstoneButton.setMessage(Component.translatable("gui.tccolonybridge.redstone",
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
        g.fill(x, y, x + WIDTH, y + HEIGHT, UiColors.BACKGROUND);
        g.fill(x, y, x + WIDTH, y + 2, UiColors.ACCENT);

        g.drawString(font, title, x + PADDING, y + 8, UiColors.ACCENT, false);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony")
                : Component.literal(snap.colonyName());
        g.drawString(font, colony, x + PADDING, y + 22, UiColors.HIGHLIGHT, false);

        Component status = Component.translatable(snap.status().guiKey());
        g.fill(x + PADDING, y + 36, x + PADDING + 4, y + 40, StatusColors.of(snap.status()));
        g.drawString(font, status, x + PADDING + 8, y + 34, UiColors.TEXT, false);

        section(g, x, y + ColonySupplyMenu.KEEP_Y - 12, Component.translatable("gui.tccolonybridge.supply.keep"));
        section(g, x, y + ColonySupplyMenu.SURPLUS_Y - 12, Component.translatable("gui.tccolonybridge.supply.surplus"));
        renderSlots(g, x, y);
        g.drawString(font, Component.translatable("gui.tccolonybridge.supply.hint"),
                x + PADDING, y + HEIGHT - 12, UiColors.TEXT_MUTED, false);
    }

    private void section(GuiGraphics g, int x, int y, Component label) {
        g.drawString(font, label, x + PADDING, y, UiColors.TEXT_MUTED, false);
    }

    /** Fundo dos slots e, sobre cada linha configurada, a quantidade alvo. */
    private void renderSlots(GuiGraphics g, int x, int y) {
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            boolean stock = slot.index < StockList.SIZE;
            g.fill(sx, sy, sx + 18, sy + 18, stock ? UiColors.ACCENT : UiColors.BORDER);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, UiColors.PANEL);
        }
        for (int i = 0; i < StockList.SIZE && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.getItem().isEmpty()) {
                continue;
            }
            String amount = UiFormat.compact(settings.amount(i));
            g.pose().pushPose();
            g.pose().translate(x + slot.x + 17f - font.width(amount) * 0.6f, y + slot.y + 10f, 200);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, amount, 0, 0, UiColors.TEXT, true);
            g.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderAmountTooltip(g, mouseX, mouseY);
    }

    /** Sobre uma linha configurada: quantidade alvo, quanto existe hoje e como mudar. */
    private void renderAmountTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int index = stockSlotAt(mouseX, mouseY);
        if (index < 0 || menu.slots.get(index).getItem().isEmpty()) {
            return;
        }
        SupplySnapshot snap = menu.getSnapshot();
        g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.tccolonybridge.supply.target", settings.amount(index)),
                Component.translatable("gui.tccolonybridge.supply.current", snap.count(index)),
                Component.translatable("gui.tccolonybridge.supply.scroll")), mouseX, mouseY);
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
