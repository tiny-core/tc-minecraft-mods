package org.tinycore.core;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
 * Também registra a config de cliente ({@link TcCoreClientConfig}: preferências de tela comuns aos mods TC).
 * <p>
 * É um mod separado (o jogador instala o jar dele junto dos mods TC); os mods declaram a dependência no
 * próprio {@code neoforge.mods.toml}.
 */
@Mod(TcCore.MOD_ID)
public final class TcCore {

    public static final String MOD_ID = "tccore";
    public static final Logger LOG = LogUtils.getLogger();

    /** {@code ModContainer} é injetado pelo NeoForge: o "dono" do mod, onde as configs são registradas. */
    public TcCore(ModContainer container) {
        // Config de cliente: o NeoForge só a carrega no cliente; no servidor dedicado fica sem efeito.
        container.registerConfig(ModConfig.Type.CLIENT, TcCoreClientConfig.SPEC);
    }
}
