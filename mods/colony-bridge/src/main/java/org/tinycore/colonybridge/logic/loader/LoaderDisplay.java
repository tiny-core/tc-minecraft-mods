package org.tinycore.colonybridge.logic.loader;

import net.minecraft.nbt.CompoundTag;

/**
 * O que a face do Chunk Loader mostra no mundo: situação, chunks carregados, consumo e minutos até soltar a área.
 * Pequeno de propósito: vai ao cliente pelo "update tag" do block entity (mecanismo nativo do Minecraft para
 * jogadores com o chunk carregado), só quando muda — no máximo 1×/s.
 *
 * @param power       consumo em AE/t, arredondado
 * @param minutesLeft minutos até soltar a área (só na contagem)
 */
public record LoaderDisplay(LoaderState state, int loaded, int power, long minutesLeft) {

    public static final LoaderDisplay EMPTY = new LoaderDisplay(LoaderState.OFF, 0, 0, 0);

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("state", state.ordinal());
        tag.putInt("loaded", loaded);
        tag.putInt("power", power);
        tag.putLong("minutes", minutesLeft);
        return tag;
    }

    /** Lê com limites (o dado vem da rede). */
    public static LoaderDisplay load(CompoundTag tag) {
        return new LoaderDisplay(LoaderState.byId(tag.getInt("state")), Math.max(0, tag.getInt("loaded")),
                Math.max(0, tag.getInt("power")), Math.max(0, tag.getLong("minutes")));
    }
}
