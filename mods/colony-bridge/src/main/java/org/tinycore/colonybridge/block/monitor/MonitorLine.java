package org.tinycore.colonybridge.block.monitor;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.tinycore.colonybridge.logic.bridge.RequestLine;
import org.tinycore.colonybridge.logic.bridge.RequestOutcome;

/**
 * Uma linha da lista de pedidos no monitor. Versão compacta do {@link RequestLine}: guarda o
 * {@link Item} (não o ItemStack), que compara por valor — assim {@code MonitorData.equals} só dá
 * diferente quando algo mudou de verdade e a tela não é reenviada à toa.
 */
public record MonitorLine(Item item, int count, RequestOutcome outcome, Component label) {

    /** Limite do texto da descrição vindo da rede (proteção contra dados gigantes). */
    private static final int MAX_LABEL_JSON = 1024;
    private static final RequestOutcome[] OUTCOMES = RequestOutcome.values();

    public static MonitorLine of(RequestLine line) {
        return new MonitorLine(line.icon().getItem(), line.count(), line.outcome(), line.label());
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
        tag.putInt("count", count);
        tag.putInt("outcome", outcome.ordinal());
        tag.putString("label", Component.Serializer.toJson(label, registries));
        return tag;
    }

    static MonitorLine load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("item"));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        String json = tag.getString("label");
        Component label = null;
        if (json.length() <= MAX_LABEL_JSON) {
            try {
                label = Component.Serializer.fromJson(json, registries);
            } catch (RuntimeException ignored) {
                // texto inválido vindo da rede: mostra sem descrição
            }
        }
        return new MonitorLine(item, tag.getInt("count"),
                OUTCOMES[Math.floorMod(tag.getInt("outcome"), OUTCOMES.length)],
                label == null ? Component.empty() : label);
    }
}
