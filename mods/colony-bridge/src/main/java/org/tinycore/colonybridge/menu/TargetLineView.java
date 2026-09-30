package org.tinycore.colonybridge.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.target.TargetLine;
import org.tinycore.colonybridge.logic.target.TargetSpec;

/**
 * Uma linha de lista como a tela a recebe: o texto do alvo, o item modelo (só em linha de item) e a
 * quantidade. É o {@link TargetLine} "achatado" para o pacote; a tela confere sozinha se o alvo existe
 * ({@code TargetResolver}), porque o cliente tem os mesmos registros e tags.
 */
public record TargetLineView(String text, ItemStack item, int amount, boolean all) {

    public static final StreamCodec<RegistryFriendlyByteBuf, TargetLineView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(TargetSpec.MAX_TEXT), TargetLineView::text,
            ItemStack.OPTIONAL_STREAM_CODEC, TargetLineView::item,
            ByteBufCodecs.VAR_INT, TargetLineView::amount,
            ByteBufCodecs.BOOL, TargetLineView::all,
            TargetLineView::new);

    public static TargetLineView of(TargetLine line) {
        return new TargetLineView(line.spec().text(), line.item(), line.amount(), line.all());
    }

    /** Alvo lido do texto (null se o texto chegou inválido). */
    public @Nullable TargetSpec spec() {
        return TargetSpec.parse(text);
    }
}
