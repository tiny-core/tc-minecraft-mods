package org.tinycore.colonybridge.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A qual colônia um tablet está ligado. Guarda a <b>colônia</b> (chave estável com a dimensão, ver
 * {@code ColonyAccess.colonyKey}), não a posição de um bloco: os blocos são achados pelo registro da colônia
 * ({@code ColonyBlockRegistry}), então mudar a Ponte de lugar não quebra o tablet.
 * <p>
 * Fica no item como "data component" ({@code ModDataComponents.TABLET_LINK}). Os dois codecs dizem como salvar
 * no disco ({@link #CODEC}, formato do Minecraft) e como mandar pela rede ({@link #STREAM_CODEC}).
 *
 * @param colony     chave da colônia ({@code dimensão#id})
 * @param colonyName nome para mostrar na dica do item (pode ficar desatualizado se a colônia for renomeada)
 */
public record TabletLink(String colony, String colonyName) {

    private static final int MAX_TEXT = 128;

    public static final Codec<TabletLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.string(0, MAX_TEXT).fieldOf("colony").forGetter(TabletLink::colony),
            Codec.string(0, MAX_TEXT).fieldOf("name").forGetter(TabletLink::colonyName)
    ).apply(instance, TabletLink::new));

    public static final StreamCodec<ByteBuf, TabletLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_TEXT), TabletLink::colony,
            ByteBufCodecs.stringUtf8(MAX_TEXT), TabletLink::colonyName,
            TabletLink::new);
}
