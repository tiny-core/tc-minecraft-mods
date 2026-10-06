package org.tinycore.core.client.ui;

import net.minecraft.network.chat.Component;
import org.tinycore.core.TcCoreClientConfig;
import org.tinycore.core.grid.GridHeight;

/**
 * Botão da barra lateral que troca a altura da grade dos terminais TC ({@link GridHeight}: 5 linhas, 8 linhas ou
 * o que couber), como o botão "estilo do terminal" do AE2. A escolha é a mesma para todos os terminais e fica
 * salva na config de cliente ({@link TcCoreClientConfig}).
 * <p>
 * O botão só troca e salva; quem refaz o layout é a tela, no {@code relayout} (em geral {@code rebuildWidgets()},
 * que chama o {@code init()} de novo com a altura nova).
 */
public final class GridHeightButton {

    private GridHeightButton() {}

    /** Cria o botão já com o ícone e o tooltip da altura atual. */
    public static IconButton create(Runnable relayout) {
        IconButton button = new IconButton(0, 0, () -> {
            TcCoreClientConfig.cycleGridHeight();
            relayout.run();
        });
        describe(button, TcCoreClientConfig.gridHeight());
        return button;
    }

    private static void describe(IconButton button, GridHeight height) {
        button.glyph(switch (height) {
            case SMALL -> "5";
            case MEDIUM -> "8";
            case TALL -> "↕";
        });
        button.setTooltipText(Component.translatable("gui.tccore.grid_height",
                Component.translatable("gui.tccore.grid_height." + height.name().toLowerCase())));
    }
}
