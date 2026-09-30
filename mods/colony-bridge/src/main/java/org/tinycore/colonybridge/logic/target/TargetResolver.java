package org.tinycore.colonybridge.logic.target;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liga um {@link TargetSpec} (só texto) aos registros do jogo: o item/tag/mod existe? quais itens a tag tem?
 * Funciona igual no servidor e no cliente (os dois têm os registros e as tags sincronizadas), então a tela
 * usa o mesmo código para pintar a linha de vermelho que o servidor usa para recusar a edição.
 * <p>
 * {@code TagKey} é o "nome" de uma tag de itens ({@code #c:ingots/iron}); quem sabe os itens de cada tag é o
 * registro. As chaves são guardadas num cache ({@link ConcurrentHashMap}, porque o render do cliente e o
 * servidor integrado rodam em threads diferentes) para não criar uma por comparação. Os itens da tag
 * <b>não</b> ficam em cache: o registro já os guarda e assim um {@code /reload} vale na hora.
 */
public final class TargetResolver {

    private static final Map<String, TagKey<Item>> TAG_KEYS = new ConcurrentHashMap<>();

    private TargetResolver() {}

    /** Chave da tag de uma linha de tag; null para item e mod. */
    public static @Nullable TagKey<Item> tagKey(TargetSpec spec) {
        if (spec.kind() != TargetKind.TAG) {
            return null;
        }
        return TAG_KEYS.computeIfAbsent(spec.id(), id -> {
            ResourceLocation location = ResourceLocation.tryParse(id);
            return location == null ? null : TagKey.create(Registries.ITEM, location);
        });
    }

    /** true se o item, a tag ou o mod existe neste modpack. */
    public static boolean exists(TargetSpec spec) {
        return switch (spec.kind()) {
            case ITEM -> {
                ResourceLocation id = spec.location();
                yield id != null && BuiltInRegistries.ITEM.containsKey(id);
            }
            case TAG -> {
                TagKey<Item> tag = tagKey(spec);
                yield tag != null && BuiltInRegistries.ITEM.getTag(tag).isPresent();
            }
            case MOD -> ModList.get().isLoaded(spec.id());
        };
    }

    /** Itens de uma tag (vazio se a tag não existe ou não é linha de tag). */
    public static List<Item> tagItems(TargetSpec spec) {
        TagKey<Item> tag = tagKey(spec);
        if (tag == null) {
            return List.of();
        }
        return BuiltInRegistries.ITEM.getTag(tag)
                .map(set -> set.stream().map(Holder::value).toList())
                .orElse(List.of());
    }

    /** Id do mod dono do item ({@code minecraft}, {@code mekanism}...). Sem alocar: vem do holder do registro. */
    public static String namespace(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().key().location().getNamespace();
    }
}
