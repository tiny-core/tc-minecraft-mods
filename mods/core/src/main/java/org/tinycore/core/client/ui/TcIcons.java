package org.tinycore.core.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.tinycore.core.TcCore;

/**
 * Ícones 16×16 dos botões das telas TC (usados com {@link IconButton#sprite}), nas cores da marca. Os PNGs ficam em
 * {@code assets/tccore/textures/gui/icons/} e são gerados por {@code tools/icons/generate_icons.py} (na raiz do
 * workspace): para mudar um desenho, edite o script e rode de novo.
 */
public final class TcIcons {

    /** Ajuda (tooltip com as instruções da tela). */
    public static final ResourceLocation HELP = of("help");
    /** Ordem por quantidade (barras decrescentes). */
    public static final ResourceLocation SORT_AMOUNT = of("sort_amount");
    /** Ordem por nome (A → Z). */
    public static final ResourceLocation SORT_NAME = of("sort_name");
    /** Aviso / mostrar itens com problema. */
    public static final ResourceLocation WARNING = of("warning");
    public static final ResourceLocation PRIORITY_UP = of("priority_up");
    public static final ResourceLocation PRIORITY_DOWN = of("priority_down");
    /** Altura da grade: 5 linhas, 8 linhas, janela toda. */
    public static final ResourceLocation GRID_SMALL = of("grid_small");
    public static final ResourceLocation GRID_MEDIUM = of("grid_medium");
    public static final ResourceLocation GRID_TALL = of("grid_tall");

    private TcIcons() {}

    /** Ícone {@code textures/gui/icons/<name>.png} de um mod (para os ícones próprios de cada mod). */
    public static ResourceLocation icon(String namespace, String name) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "textures/gui/icons/" + name + ".png");
    }

    private static ResourceLocation of(String name) {
        return icon(TcCore.MOD_ID, name);
    }
}
