package org.tinycore.cloud.integration;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.block.CloudLinkBlockEntity;
import org.tinycore.cloud.block.LinkNetwork;
import org.tinycore.cloud.integration.ae2.Ae2Bridge;
import org.tinycore.cloud.item.ContentProbe;

import java.util.List;

/**
 * O AE2 é <b>opcional</b>: esta classe diz se ele está instalado e só então chama o {@link Ae2Bridge}. A JVM só
 * carrega uma classe quando o código que a usa roda pela primeira vez; como as chamadas ao {@code Ae2Bridge} ficam
 * atrás de {@link #LOADED}, sem o AE2 nenhuma classe dele é carregada (e o jogo não quebra).
 * <p>
 * Com o AE2: o Link monta o canal na rede (modos e prioridade). Sem ele: o Link funciona só pela tela.
 */
public final class Ae2Compat {

    /** true se o AE2 está instalado (lido uma vez, depois do carregamento dos mods). */
    public static final boolean LOADED = ModList.get().isLoaded("ae2");

    private Ae2Compat() {}

    public static void init(@NotNull IEventBus modBus) {
        if (LOADED) Ae2Bridge.init(modBus);
    }

    /** A ligação do Link com a rede AE2, ou {@link LinkNetwork#NONE} sem o AE2. */
    public static @NotNull LinkNetwork network(@NotNull CloudLinkBlockEntity link) {
        return LOADED ? Ae2Bridge.network(link) : LinkNetwork.NONE;
    }

    /** Sondas de conteúdo de itens do AE2 (vazio sem ele). */
    public static @NotNull List<ContentProbe> contentProbes() {
        return LOADED ? Ae2Bridge.contentProbes() : List.of();
    }
}
