package org.tinycore.colonybridge.client.render;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.block.monitor.MonitorLine;
import org.tinycore.colonybridge.client.ui.StatusColors;
import org.tinycore.colonybridge.client.ui.UiColors;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lista paginada de pedidos na tela do monitor: ícone, descrição, quantidade e resultado colorido.
 * <p>
 * Quantas linhas cabem depende da área (1 ou 2 colunas); o número de páginas sai daí. A página atual
 * vem do block entity (troca automática + clique), calculada com módulo, então nunca sai do intervalo.
 */
final class MonitorRequestList {

    private static final int ROW_HEIGHT = 20;
    private static final int FOOTER = 10;
    private static final int GAP = 3;
    /** Colunas a partir desta largura (em pixels de tela = 4 blocos). */
    private static final int TWO_COLUMNS_FROM = MonitorRenderer.PIXELS_PER_BLOCK * 4;

    /**
     * ItemStack por Item, reaproveitado entre frames (o render roda a cada frame; criar stacks
     * novos ali seria alocação num laço quente). Itens são únicos no registro, então o mapa é limitado.
     */
    private static final Map<Item, ItemStack> ICONS = new IdentityHashMap<>();

    private MonitorRequestList() {}

    /** Quantos pedidos cabem numa página nesta área (0 se não cabe nenhum). */
    static int perPage(float width, float height) {
        int rows = (int) ((height - FOOTER) / ROW_HEIGHT);
        return Math.max(0, rows) * columns(width);
    }

    private static int columns(float width) {
        return width >= TWO_COLUMNS_FROM ? 2 : 1;
    }

    /**
     * @param page      página já normalizada (0..páginas-1)
     * @param total     total de pedidos em aberto (a lista pode estar cortada no limite de sincronização)
     */
    static void render(MonitorCanvas c, List<MonitorLine> lines, int total, int page,
                       float x, float y, float width, float height) {
        if (lines.isEmpty()) {
            c.textCentered(Component.translatable("monitor.tccolonybridge.no_requests"), x + width / 2f,
                    y + height / 2f - 4, UiColors.TEXT_MUTED, 0.8f, width, 2);
            return;
        }
        int perPage = perPage(width, height);
        if (perPage == 0) {
            return;
        }
        int columns = columns(width);
        int rows = perPage / columns;
        float columnWidth = (width - GAP * (columns - 1)) / columns;
        int first = page * perPage;
        for (int i = 0; i < perPage && first + i < lines.size(); i++) {
            int column = i / rows;
            int row = i % rows;
            row(c, lines.get(first + i), x + column * (columnWidth + GAP), y + row * ROW_HEIGHT, columnWidth);
        }
        footer(c, lines.size(), total, perPage, page, x, y + height - FOOTER + 2, width);
    }

    private static void row(MonitorCanvas c, MonitorLine line, float x, float y, float width) {
        c.fill(x, y, x + width, y + ROW_HEIGHT - 2, UiColors.PANEL, 1);
        c.item(ICONS.computeIfAbsent(line.item(), ItemStack::new), x + 2, y + 2, 14, 2);
        String count = "x" + line.count();
        float countWidth = c.width(Component.literal(count)) * 0.7f;
        c.text(Component.literal(count), x + width - 3 - countWidth, y + 3, UiColors.TEXT_MUTED, 0.7f, 2);
        float textX = x + 19;
        c.textFitted(line.label(), textX, y + 3, UiColors.TEXT, 0.7f, width - 19 - countWidth - 6, 2);
        c.textFitted(Component.translatable(line.outcome().translationKey()), textX, y + 11,
                StatusColors.of(line.outcome()), 0.55f, width - 22, 2);
    }

    /** Rodapé: "< 1/3 >" no centro e "+N" à direita quando há mais pedidos do que os sincronizados. */
    private static void footer(MonitorCanvas c, int shown, int total, int perPage, int page,
                               float x, float y, float width) {
        int pages = (shown + perPage - 1) / perPage;
        if (pages > 1) {
            c.textCentered(Component.literal("<  " + (page + 1) + "/" + pages + "  >"), x + width / 2f, y,
                    UiColors.TEXT_MUTED, 0.7f, width / 2f, 2);
        }
        if (total > shown) {
            Component more = Component.translatable("monitor.tccolonybridge.more", total - shown);
            c.text(more, x + width - c.width(more) * 0.6f, y + 1, UiColors.TEXT_MUTED, 0.6f, 2);
        }
    }

    /** Número de páginas para {@code count} pedidos nesta área. */
    static int pages(int count, float width, float height) {
        int perPage = perPage(width, height);
        return perPage == 0 ? 0 : (count + perPage - 1) / perPage;
    }
}
