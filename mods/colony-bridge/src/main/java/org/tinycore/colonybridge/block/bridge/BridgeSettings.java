package org.tinycore.colonybridge.block.bridge;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.core.block.RedstoneMode;

/**
 * Configurações de uma ponte, salvas no NBT do block entity e alteradas pela tela.
 * Imutável (record): uma mudança cria um novo objeto ({@code withX}), então não há estado meio alterado.
 * A lista de itens do filtro fica em {@link ItemFilter}; aqui só o modo e o tipo de comparação.
 *
 * @param craftingEnabled se a ponte pode agendar autocrafting
 * @param redstoneMode    quando a ponte funciona em relação à redstone
 * @param filterMode      como a lista do filtro é usada
 * @param exactMatch      filtro compara componentes (encantamentos, durabilidade) além do tipo do item
 */
public record BridgeSettings(boolean craftingEnabled, RedstoneMode redstoneMode,
                             FilterMode filterMode, boolean exactMatch) {

    public static final BridgeSettings DEFAULT =
            new BridgeSettings(true, RedstoneMode.IGNORED, FilterMode.OFF, false);

    /**
     * Usado na tela (servidor → cliente) e no pacote de mudança (cliente → servidor).
     * Enums viajam como número; valores inválidos viram o padrão ({@code byId}), nunca erro.
     */
    public static final StreamCodec<ByteBuf, BridgeSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BridgeSettings::craftingEnabled,
            ByteBufCodecs.idMapper(RedstoneMode::byId, RedstoneMode::ordinal), BridgeSettings::redstoneMode,
            ByteBufCodecs.idMapper(FilterMode::byId, FilterMode::ordinal), BridgeSettings::filterMode,
            ByteBufCodecs.BOOL, BridgeSettings::exactMatch,
            BridgeSettings::new);

    public BridgeSettings withCrafting(boolean value) {
        return new BridgeSettings(value, redstoneMode, filterMode, exactMatch);
    }

    public BridgeSettings withRedstone(RedstoneMode value) {
        return new BridgeSettings(craftingEnabled, value, filterMode, exactMatch);
    }

    public BridgeSettings withFilterMode(FilterMode value) {
        return new BridgeSettings(craftingEnabled, redstoneMode, value, exactMatch);
    }

    public BridgeSettings withExactMatch(boolean value) {
        return new BridgeSettings(craftingEnabled, redstoneMode, filterMode, value);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("crafting", craftingEnabled);
        tag.putInt("redstone", redstoneMode.ordinal());
        tag.putInt("filterMode", filterMode.ordinal());
        tag.putBoolean("exactMatch", exactMatch);
        return tag;
    }

    /** Lê do NBT; campos ausentes (pontes antigas) ficam com o valor padrão. */
    public static BridgeSettings load(CompoundTag tag) {
        boolean crafting = !tag.contains("crafting") || tag.getBoolean("crafting");
        return new BridgeSettings(crafting, RedstoneMode.byId(tag.getInt("redstone")),
                FilterMode.byId(tag.getInt("filterMode")), tag.getBoolean("exactMatch"));
    }
}
