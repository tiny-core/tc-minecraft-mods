package org.tinycore.core;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.tinycore.core.grid.GridHeight;

/**
 * Config de <b>cliente</b> do core ({@code config/tccore-client.toml}): preferências de tela que valem para todos
 * os mods TC e ficam só na máquina do jogador (o servidor não lê nem sincroniza config de cliente).
 * <p>
 * {@code ModConfigSpec} ≈ um "appsettings" tipado: cada {@code define} cria uma chave com valor padrão e
 * comentário. Registrada pelo {@link TcCore}.
 */
public final class TcCoreClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Altura da grade dos terminais TC (Terminal do Armazém, TC Cloud Link...), trocada pelo botão da tela. */
    public static final ModConfigSpec.EnumValue<GridHeight> GRID_HEIGHT = BUILDER
            .comment("Altura da grade dos terminais TC: SMALL (5 linhas), MEDIUM (8) ou TALL (o que couber na janela).")
            .defineEnum("gridHeight", GridHeight.SMALL);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TcCoreClientConfig() {}

    /** Altura atual; o padrão se a config ainda não carregou (ex.: tela aberta cedo demais). */
    public static GridHeight gridHeight() {
        return SPEC.isLoaded() ? GRID_HEIGHT.get() : GridHeight.SMALL;
    }

    /** Passa para a próxima altura e salva no arquivo (sobrevive a reiniciar o jogo, como no AE2). */
    public static GridHeight cycleGridHeight() {
        GridHeight next = gridHeight().next();
        if (SPEC.isLoaded()) {
            GRID_HEIGHT.set(next);
            GRID_HEIGHT.save();
        }
        return next;
    }
}
