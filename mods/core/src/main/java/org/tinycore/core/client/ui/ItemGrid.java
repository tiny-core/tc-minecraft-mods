package org.tinycore.core.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tinycore.core.grid.ItemListing;

import java.util.List;

/**
 * Grade de itens rolável das telas TC (só cliente): lista filtrada/ordenada, rolagem, células com a
 * quantidade no canto e "qual entrada está sob o mouse". Usada pelo Terminal do Armazém (Colony Bridge) e
 * pelo TC Cloud Link (Cloud Storage).
 *
 * <p>Genérica no tipo da entrada ({@code <T>} ≈ generic de C#): cada tela diz, por um {@link Adapter}, como
 * ler nome, mod, quantidade e ícone das suas entradas. Uma entrada pode não ter {@code ItemStack} (item de um
 * mod ausente neste servidor): aí a célula mostra um ícone substituto. Entradas "apagadas" ({@link
 * Adapter#dimmed}) ganham um véu escuro por cima, para indicar que não podem ser usadas.
 *
 * <p>A busca e a ordenação ({@link ItemListing}) só são refeitas quando algo muda (versão dos dados, texto da
 * busca ou ordem), nunca a cada frame.
 */
public final class ItemGrid<T> {

    /** Como a grade lê uma entrada da tela dona. */
    public interface Adapter<T> {
        /** Nome visível (busca, ordem e tooltip). */
        @NotNull String name(@NotNull T entry);

        /** Id do mod dono do item (busca com {@code @}). */
        @NotNull String modId(@NotNull T entry);

        long amount(@NotNull T entry);

        /** Ícone; {@code null} quando o item não existe neste jogo (desenha o substituto). */
        @Nullable ItemStack icon(@NotNull T entry);

        /** Entrada que não pode ser usada (desenhada com véu). */
        default boolean dimmed(@NotNull T entry) {
            return false;
        }
    }

    private static final int CELL = 18;
    /** Véu sobre células apagadas: o fundo da janela, semitransparente. */
    private static final int DIM_VEIL = 0xB01B1E26;
    private static final ItemStack MISSING_ICON = new ItemStack(Items.BARRIER);

    private final int x;
    private final int y;
    private final int columns;
    private final int rows;
    private final Adapter<T> adapter;

    private List<T> visible = List.of();
    private int seenVersion = -1;
    private String seenQuery = "";
    private ItemListing.Sort seenSort = ItemListing.Sort.AMOUNT;
    private int firstRow;

    /** {@code x}/{@code y}: canto de cima/esquerda da primeira célula, já em coordenadas de tela. */
    public ItemGrid(int x, int y, int columns, int rows, @NotNull Adapter<T> adapter) {
        this.x = x;
        this.y = y;
        this.columns = columns;
        this.rows = rows;
        this.adapter = adapter;
    }

    /**
     * Refaz a lista visível se os dados, a busca ou a ordem mudaram desde a última vez.
     *
     * @param version número que a tela aumenta a cada mudança em {@code entries} (ex.: pacote novo)
     */
    public void update(int version, @NotNull List<T> entries, @NotNull String query, @NotNull ItemListing.Sort sort) {
        if (version == seenVersion && query.equals(seenQuery) && sort == seenSort) {
            return;
        }
        boolean searchChanged = !query.equals(seenQuery) || sort != seenSort;
        seenVersion = version;
        seenQuery = query;
        seenSort = sort;
        visible = ItemListing.filterAndSort(entries, query, sort, adapter::name, adapter::modId, this::tagsOf,
                adapter::amount);
        if (searchChanged) {
            firstRow = 0; // nova busca começa do topo; atualização de dados mantém a posição
        }
        firstRow = Math.min(firstRow, maxFirstRow());
    }

    /** Tags do ícone da entrada (busca com {@code #}); entrada sem ícone (item ausente) não tem tags. */
    private List<String> tagsOf(T entry) {
        ItemStack icon = adapter.icon(entry);
        return icon == null ? List.of() : icon.getTags().map(tag -> tag.location().toString()).toList();
    }

    public void render(@NotNull GuiGraphics g, @NotNull Font font, int mouseX, int mouseY) {
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
    private void drawEntry(GuiGraphics g, Font font, T entry, int cellX, int cellY) {
        ItemStack icon = adapter.icon(entry);
        g.renderItem(icon != null ? icon : MISSING_ICON, cellX, cellY);
        if (adapter.dimmed(entry)) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 190); // acima do ícone, abaixo da quantidade
            g.fill(cellX, cellY, cellX + 16, cellY + 16, DIM_VEIL);
            g.pose().popPose();
        }
        String amount = UiFormat.compact(adapter.amount(entry));
        g.pose().pushPose();
        // z 200: acima do ícone (itens são desenhados até z ~150)
        g.pose().translate(cellX + 16f - font.width(amount) * 0.6f, cellY + 11f, 200);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(font, amount, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    /** true se o mouse está sobre a grade (células, sem a barra de rolagem). */
    public boolean isOver(double mouseX, double mouseY) {
        return mouseX >= x - 1 && mouseX < x + columns * CELL - 1 && mouseY >= y - 1 && mouseY < y + rows * CELL - 1;
    }

    /** Entrada sob o mouse, ou null (fora da grade ou célula vazia). */
    public @Nullable T entryAt(double mouseX, double mouseY) {
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

    /** Célula ocupada sob o mouse com item existente (para o JEI mostrar receita/usos), ou null. */
    public @Nullable Hit hitAt(double mouseX, double mouseY) {
        T entry = entryAt(mouseX, mouseY);
        ItemStack icon = entry == null ? null : adapter.icon(entry);
        if (icon == null) {
            return null;
        }
        int column = (int) (mouseX - x + 1) / CELL;
        int row = (int) (mouseY - y + 1) / CELL;
        return new Hit(icon, x + column * CELL, y + row * CELL);
    }

    /** Roda do mouse: uma linha por passo. */
    public void scroll(double delta) {
        firstRow = Math.max(0, Math.min(maxFirstRow(), firstRow - (int) Math.signum(delta)));
    }

    private int totalRows() {
        return (visible.size() + columns - 1) / columns;
    }

    private int maxFirstRow() {
        return Math.max(0, totalRows() - rows);
    }
}
