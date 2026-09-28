package org.tinycore.colonybridge.client.supply;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.client.ui.FlatButton;
import org.tinycore.colonybridge.client.ui.ScreenStyle;
import org.tinycore.colonybridge.client.ui.StatusColors;
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
    private static final int HEIGHT = 226;
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
        // Linha própria, largura toda: não disputa espaço com o nome da colônia (que pode ser longo).
        redstoneButton = addRenderableWidget(new FlatButton(leftPos + PADDING, topPos + 34, WIDTH - PADDING * 2, 14,
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
        int inner = WIDTH - PADDING * 2;
        ScreenStyle.window(g, x, y, WIDTH, HEIGHT);

        // Estado à direita primeiro: o título usa o espaço que sobrar (corta com "…" se preciso).
        Component status = Component.translatable(snap.status().guiKey());
        int statusWidth = ScreenStyle.drawFittedRight(g, font, status, x + WIDTH - PADDING, y + 8, inner / 2,
                ScreenStyle.TEXT);
        ScreenStyle.statusDot(g, x + WIDTH - PADDING - statusWidth - 10, y + 8, StatusColors.of(snap.status()));
        ScreenStyle.drawFitted(g, font, title, x + PADDING, y + 8, inner - statusWidth - 16, ScreenStyle.TEXT);
        Component colony = snap.colonyName().isEmpty()
                ? Component.translatable("gui.tccolonybridge.no_colony")
                : Component.literal(snap.colonyName());
        ScreenStyle.drawFitted(g, font, colony, x + PADDING, y + 20, inner, ScreenStyle.INFO);

        section(g, x, y + ColonySupplyMenu.KEEP_Y - 11, Component.translatable("gui.tccolonybridge.supply.keep"));
        section(g, x, y + ColonySupplyMenu.SURPLUS_Y - 11, Component.translatable("gui.tccolonybridge.supply.surplus"));
        section(g, x, y + ColonySupplyMenu.INVENTORY_Y - 11, Component.translatable("gui.tccolonybridge.inventory"));
        renderSlots(g, x, y);
        ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.supply.hint"),
                x + PADDING, y + HEIGHT - 13, inner, ScreenStyle.TEXT_MUTED);
    }

    /** Título de seção alinhado com os slots, como no terminal do AE2. */
    private void section(GuiGraphics g, int x, int y, Component label) {
        ScreenStyle.drawFitted(g, font, label, x + ColonySupplyMenu.LIST_X, y,
                WIDTH - ColonySupplyMenu.LIST_X - PADDING, ScreenStyle.TEXT);
    }

    /** Fundo dos slots e, sobre cada linha configurada, a quantidade alvo. */
    private void renderSlots(GuiGraphics g, int x, int y) {
        for (Slot slot : menu.slots) {
            ScreenStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
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
            g.drawString(font, amount, 0, 0, 0xFFFFFFFF, true); // branco com sombra, como a contagem de itens
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
