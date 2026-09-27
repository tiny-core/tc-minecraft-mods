package org.tinycore.colonybridge.block;

import net.minecraft.nbt.CompoundTag;

/**
 * Configurações de uma ponte, salvas no NBT do block entity e alteradas pela tela.
 * Imutável (record): uma mudança cria um novo objeto, então não há estado meio alterado.
 *
 * @param craftingEnabled se a ponte pode agendar autocrafting
 * @param redstoneMode    quando a ponte funciona em relação à redstone
 */
public record BridgeSettings(boolean craftingEnabled, RedstoneMode redstoneMode) {

    public static final BridgeSettings DEFAULT = new BridgeSettings(true, RedstoneMode.IGNORED);

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("crafting", craftingEnabled);
        tag.putInt("redstone", redstoneMode.ordinal());
        return tag;
    }

    /** Lê do NBT; campos ausentes (pontes antigas) ficam com o valor padrão. */
    public static BridgeSettings load(CompoundTag tag) {
        boolean crafting = !tag.contains("crafting") || tag.getBoolean("crafting");
        return new BridgeSettings(crafting, RedstoneMode.byId(tag.getInt("redstone")));
    }
}
