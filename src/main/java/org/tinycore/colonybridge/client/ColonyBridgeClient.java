package org.tinycore.colonybridge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.tinycore.colonybridge.ColonyBridgeMod;

/**
 * Entrada do mod <b>só no cliente</b> ({@code dist = Dist.CLIENT}: o servidor dedicado nem carrega esta
 * classe). Liga o botão "Config" da lista de mods à tela de configuração pronta do NeoForge.
 * <p>
 * A tela edita a config do servidor ({@code tccolonybridge-server.toml}) em mundo single player ou no host
 * de uma LAN; num servidor dedicado o NeoForge mostra que é preciso editar o arquivo (que também é
 * recarregado sozinho, sem reiniciar). Os nomes e dicas de cada opção vêm das chaves
 * {@code tccolonybridge.configuration.*} nos arquivos de idioma.
 */
@Mod(value = ColonyBridgeMod.MOD_ID, dist = Dist.CLIENT)
public final class ColonyBridgeClient {

    public ColonyBridgeClient(ModContainer container) {
        // "Extension point" ≈ um serviço registrado por mod; aqui, a fábrica da tela de config.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
