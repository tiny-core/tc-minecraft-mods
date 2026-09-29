package org.tinycore.colonybridge.client.bridge;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.client.ui.ScreenStyle;

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
            ScreenStyle.drawFitted(g, font, Component.translatable("gui.tccolonybridge.mods.empty"), x + 2, y + 2,
                    width - 4, ScreenStyle.TEXT_MUTED);
            return;
        }
        int rows = visibleRows(height);
        for (int i = 0; i < rows && scroll + i < mods.size(); i++) {
            String mod = mods.get(scroll + i);
            int rowY = y + i * ROW_HEIGHT;
            if (mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                g.fill(x, rowY, x + width, rowY + ROW_HEIGHT, ScreenStyle.HOVER);
            }
            int boxY = rowY + (ROW_HEIGHT - BOX) / 2;
            ScreenStyle.inset(g, x + 2, boxY, BOX, BOX, marked.contains(mod) ? ScreenStyle.ACCENT : ScreenStyle.PANEL);
            // id à direita (até 40% da linha) e nome no espaço que sobrar, ambos cortados com "…"
            int idWidth = ScreenStyle.drawFittedRight(g, font, Component.literal(mod), x + width - 2, rowY + 2,
                    width * 2 / 5, ScreenStyle.TEXT_MUTED);
            ScreenStyle.drawFitted(g, font, Component.literal(displayName(mod)), x + BOX + 6, rowY + 2,
                    width - BOX - idWidth - 14, ScreenStyle.TEXT);
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

    /** Quantas linhas cabem na altura dada. */
    int visibleRows(int height) {
        return Math.max(1, height / ROW_HEIGHT);
    }

    /** Primeira linha visível (para a barra de rolagem). */
    int firstRow() {
        return scroll;
    }

    int size() {
        return mods.size();
    }

    /** Nome bonito do mod (ex.: "Applied Energistics 2"); se o mod não estiver instalado no cliente, o id. */
    private static String displayName(String mod) {
        return ModList.get().getModContainerById(mod)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(mod);
    }
}
