package org.tinycore.colonybridge.client.bridge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.client.ui.UiColors;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lista rolável de mods da aba "Mods" da tela da ponte: uma linha por mod, com caixa de marcar, nome
 * e id. Mostra os mods craftáveis da rede ({@code BridgeSnapshot.craftableMods}) mais os já marcados que
 * sumiram da rede, para o jogador ainda poder desmarcá-los.
 * <p>
 * Só desenha e diz em qual mod o jogador clicou; quem manda o pacote é a {@link ColonyBridgeScreen}.
 */
final class ModListView {

    private static final int ROW_HEIGHT = 12;
    private static final int BOX = 8;

    private final Font font;
    private List<String> mods = List.of();
    private int scroll;
    /** Altura da última renderização, para limitar a rolagem quando a lista muda. */
    private int lastHeight = ROW_HEIGHT;

    ModListView(Font font) {
        this.font = font;
    }

    /** Atualiza as linhas (chamado quando chega um snapshot novo). */
    void setMods(List<String> craftable, List<String> marked) {
        Set<String> all = new TreeSet<>(craftable);
        all.addAll(marked);
        mods = List.copyOf(all);
        scroll = Math.min(scroll, maxScroll(lastHeight));
    }

    void render(GuiGraphics g, Set<String> marked, int x, int y, int width, int height, int mouseX, int mouseY) {
        lastHeight = height;
        if (mods.isEmpty()) {
            g.drawString(font, Component.translatable("gui.tccolonybridge.mods.empty"), x, y + 2,
                    UiColors.TEXT_MUTED, false);
            return;
        }
        int rows = visibleRows(height);
        for (int i = 0; i < rows && scroll + i < mods.size(); i++) {
            String mod = mods.get(scroll + i);
            int rowY = y + i * ROW_HEIGHT;
            if (mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                g.fill(x, rowY, x + width, rowY + ROW_HEIGHT, UiColors.PANEL_HOVER);
            }
            boolean checked = marked.contains(mod);
            int boxY = rowY + (ROW_HEIGHT - BOX) / 2;
            g.fill(x + 2, boxY, x + 2 + BOX, boxY + BOX, checked ? UiColors.ACCENT : UiColors.BORDER);
            g.fill(x + 3, boxY + 1, x + 1 + BOX, boxY + BOX - 1, checked ? UiColors.ACCENT : UiColors.PANEL);
            int idWidth = font.width(mod);
            int nameWidth = width - idWidth - BOX - 14; // corta o nome para não encostar no id
            g.drawString(font, font.plainSubstrByWidth(displayName(mod), nameWidth), x + BOX + 6, rowY + 2,
                    UiColors.TEXT, false);
            g.drawString(font, mod, x + width - idWidth - 2, rowY + 2, UiColors.TEXT_MUTED, false);
        }
        if (mods.size() > rows) {
            Component more = Component.literal((scroll + 1) + "–" + Math.min(scroll + rows, mods.size())
                    + " / " + mods.size());
            g.drawString(font, more, x + width - font.width(more), y + height + 2, UiColors.TEXT_MUTED, false);
        }
    }

    /** Mod na posição do clique, ou null se o clique foi fora das linhas. */
    @Nullable String modAt(double mouseX, double mouseY, int x, int y, int width, int height) {
        if (mouseX < x || mouseX >= x + width || mouseY < y) {
            return null;
        }
        int row = (int) ((mouseY - y) / ROW_HEIGHT);
        int index = scroll + row;
        return row < visibleRows(height) && index < mods.size() ? mods.get(index) : null;
    }

    void scroll(double amount, int height) {
        scroll = Math.max(0, Math.min(maxScroll(height), scroll - (int) Math.signum(amount)));
    }

    private int maxScroll(int height) {
        return Math.max(0, mods.size() - visibleRows(height));
    }

    private static int visibleRows(int height) {
        return Math.max(1, height / ROW_HEIGHT);
    }

    /** Nome bonito do mod (ex.: "Applied Energistics 2"); se o mod não estiver instalado no cliente, o id. */
    private static String displayName(String mod) {
        return ModList.get().getModContainerById(mod)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(mod);
    }
}
