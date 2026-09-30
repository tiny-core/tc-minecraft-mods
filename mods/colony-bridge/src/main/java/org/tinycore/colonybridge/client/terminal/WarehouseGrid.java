package org.tinycore.colonybridge.client.terminal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.terminal.ItemListing;
import org.tinycore.colonybridge.menu.terminal.WarehouseEntry;
import org.tinycore.colonybridge.menu.terminal.WarehouseView;
import org.tinycore.core.client.ui.ScreenStyle;
import org.tinycore.core.client.ui.UiFormat;

import java.util.List;

/**
 * A grade de itens do Terminal do Armazém (só cliente): lista filtrada/ordenada, rolagem, desenho das
 * células com a quantidade no canto e "qual entrada está sob o mouse".
 * <p>
 * A busca e a ordenação ({@link ItemListing}) só são refeitas quando algo muda — novo pacote do servidor
 * ({@link WarehouseView#version()}), texto da busca ou ordem —, nunca a cada frame.
 */
public final class WarehouseGrid {

    private static final int CELL = 18;

    private final int x;
    private final int y;
    private final int columns;
    private final int rows;

    private List<WarehouseEntry> visible = List.of();
    private int seenVersion = -1;
    private String seenQuery = "";
    private ItemListing.Sort seenSort = ItemListing.Sort.AMOUNT;
    private int firstRow;

    /** {@code x}/{@code y}: canto de cima/esquerda da primeira célula, já em coordenadas de tela. */
    WarehouseGrid(int x, int y, int columns, int rows) {
        this.x = x;
        this.y = y;
        this.columns = columns;
        this.rows = rows;
    }

    /** Refaz a lista visível se a view, a busca ou a ordem mudaram desde a última vez. */
    void update(WarehouseView view, String query, ItemListing.Sort sort) {
        if (view.version() == seenVersion && query.equals(seenQuery) && sort == seenSort) {
            return;
        }
        boolean searchChanged = !query.equals(seenQuery) || sort != seenSort;
        seenVersion = view.version();
        seenQuery = query;
        seenSort = sort;
        visible = ItemListing.filterAndSort(view.entries(), query, sort,
                entry -> entry.item().getHoverName().getString(),
                entry -> BuiltInRegistries.ITEM.getKey(entry.item().getItem()).getNamespace(),
                WarehouseEntry::count);
        if (searchChanged) {
            firstRow = 0; // nova busca começa do topo; atualização do servidor mantém a posição
        }
        firstRow = Math.min(firstRow, maxFirstRow());
    }

    void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int cellX = x + column * CELL;
                int cellY = y + row * CELL;
                ScreenStyle.slot(g, cellX - 1, cellY - 1);
                int index = (firstRow + row) * columns + column;
                if (index < visible.size()) {
                    drawEntry(g, font, visible.get(index), cellX, cellY);
                }
                if (mouseX >= cellX && mouseX < cellX + 16 && mouseY >= cellY && mouseY < cellY + 16) {
                    AbstractContainerScreen.renderSlotHighlight(g, cellX, cellY, 0);
                }
            }
        }
        ScreenStyle.scrollbar(g, x + columns * CELL + 2, y - 1, rows * CELL, firstRow, rows, totalRows());
    }

    /** Ícone e quantidade compacta (1.2k, 35M) em fonte pequena no canto, como no AE2. */
    private static void drawEntry(GuiGraphics g, Font font, WarehouseEntry entry, int cellX, int cellY) {
        g.renderItem(entry.item(), cellX, cellY);
        String amount = UiFormat.compact(entry.count());
        g.pose().pushPose();
        // z 200: acima do ícone (itens são desenhados até z ~150)
        g.pose().translate(cellX + 16f - font.width(amount) * 0.6f, cellY + 11f, 200);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(font, amount, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    /** true se o mouse está sobre a grade (células, sem a barra de rolagem). */
    boolean isOver(double mouseX, double mouseY) {
        return mouseX >= x - 1 && mouseX < x + columns * CELL - 1 && mouseY >= y - 1 && mouseY < y + rows * CELL - 1;
    }

    /** Entrada sob o mouse, ou null (fora da grade ou célula vazia). */
    @Nullable WarehouseEntry entryAt(double mouseX, double mouseY) {
        if (!isOver(mouseX, mouseY)) {
            return null;
        }
        int column = (int) (mouseX - x + 1) / CELL;
        int row = (int) (mouseY - y + 1) / CELL;
        int index = (firstRow + row) * columns + column;
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    /** Uma célula ocupada da grade: o item e o canto da célula (16×16) na tela. */
    public record Hit(ItemStack item, int x, int y) {}

    /** Célula ocupada sob o mouse (para o JEI mostrar receita/usos), ou null. */
    @Nullable Hit hitAt(double mouseX, double mouseY) {
        WarehouseEntry entry = entryAt(mouseX, mouseY);
        if (entry == null) {
            return null;
        }
        int column = (int) (mouseX - x + 1) / CELL;
        int row = (int) (mouseY - y + 1) / CELL;
        return new Hit(entry.item(), x + column * CELL, y + row * CELL);
    }

    /** Roda do mouse: uma linha por passo. */
    void scroll(double delta) {
        firstRow = Math.max(0, Math.min(maxFirstRow(), firstRow - (int) Math.signum(delta)));
    }

    private int totalRows() {
        return (visible.size() + columns - 1) / columns;
    }

    private int maxFirstRow() {
        return Math.max(0, totalRows() - rows);
    }
}
