package org.tinycore.core;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entrada do <b>TC Core</b>, a biblioteca comum dos mods TC (Colony Bridge e os próximos). Não registra
 * blocos nem itens: só oferece código compartilhado, para cada mod não repetir o mesmo trabalho:
 * <ul>
 *   <li>{@code client.ui}: design system das telas (cores da marca, estilo, botões com ícone, barra lateral,
 *       gráfico de barras, grade de itens);</li>
 *   <li>{@code grid}: busca/ordem e diferença de contagens das grades de itens;</li>
 *   <li>{@code menu}: ghost slots seguros contra duplicação;</li>
 *   <li>{@code block}: modo de redstone;</li>
 *   <li>{@code stats}: contadores numa janela de tempo (ring buffer).</li>
 * </ul>
 * É um mod separado (o jogador instala o jar dele junto dos mods TC); os mods declaram a dependência no
 * próprio {@code neoforge.mods.toml}.
 */
@Mod(TcCore.MOD_ID)
public final class TcCore {

    public static final String MOD_ID = "tccore";
    public static final Logger LOG = LogUtils.getLogger();

    public TcCore() {
        // Nada a registrar por enquanto: o core é só código compartilhado.
    }
}
