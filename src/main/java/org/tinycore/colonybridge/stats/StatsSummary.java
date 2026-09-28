package org.tinycore.colonybridge.stats;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Resumo pronto para exibir das estatísticas de uma ponte (vai no snapshot da tela e, no futuro,
 * para os monitores). Só números e ids de itens: compacto e sem nada que o cliente possa explorar.
 *
 * @param windowHours tamanho da janela em horas (24 com a config padrão)
 * @param totals      somas da última hora e da janela inteira
 * @param chart       itens entregues por grupo de tempo, do mais antigo ao mais recente
 * @param top         itens mais entregues na janela
 */
public record StatsSummary(int windowHours, Totals totals, List<Integer> chart, List<Top> top) {

    /** Máximo de itens no ranking e de barras no gráfico (limite de tamanho do pacote). */
    public static final int MAX_TOP = 6;
    public static final int MAX_CHART = 48;

    public static final StatsSummary EMPTY = new StatsSummary(0, Totals.ZERO, List.of(), List.of());

    public record Totals(long itemsLastHour, long requestsLastHour, long itemsWindow, long requestsWindow,
                         long craftsStarted, long craftsFailed) {

        public static final Totals ZERO = new Totals(0, 0, 0, 0, 0, 0);

        static final StreamCodec<RegistryFriendlyByteBuf, Totals> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Totals::itemsLastHour,
                ByteBufCodecs.VAR_LONG, Totals::requestsLastHour,
                ByteBufCodecs.VAR_LONG, Totals::itemsWindow,
                ByteBufCodecs.VAR_LONG, Totals::requestsWindow,
                ByteBufCodecs.VAR_LONG, Totals::craftsStarted,
                ByteBufCodecs.VAR_LONG, Totals::craftsFailed,
                Totals::new);
    }

    /** Item do ranking. {@code ByteBufCodecs.registry} manda o id numérico do item, não o objeto. */
    public record Top(Item item, long count) {

        static final StreamCodec<RegistryFriendlyByteBuf, Top> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Top::item,
                ByteBufCodecs.VAR_LONG, Top::count,
                Top::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSummary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StatsSummary::windowHours,
            Totals.STREAM_CODEC, StatsSummary::totals,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_CHART)), StatsSummary::chart,
            Top.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOP)), StatsSummary::top,
            StatsSummary::new);
}
