package org.tinycore.colonybridge.client.list;

import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.target.TargetResolver;
import org.tinycore.colonybridge.logic.target.TargetSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ícone e nome de um alvo de lista, no cliente (tela do Abastecedor/Ponte e painel do monitor):
 * <ul>
 *   <li>item → o próprio item (barreira se ele não existe mais);</li>
 *   <li>tag → alterna entre os itens da tag, um por segundo;</li>
 *   <li>mod → alterna entre os itens do mod.</li>
 * </ul>
 * Tudo no cliente, sem pacotes: o cliente tem os mesmos registros e tags do servidor. As pilhas de ícone ficam
 * em cache (uma por item) para não alocar a cada quadro; a lista de itens de cada mod é montada uma vez.
 */
public final class TargetIcons {

    private static final int CYCLE_MS = 1000;
    private static final ItemStack INVALID = new ItemStack(Items.BARRIER);

    private static final Map<Item, ItemStack> STACKS = new HashMap<>();
    private static final Map<String, List<Item>> MOD_ITEMS = new HashMap<>();

    private TargetIcons() {}

    /**
     * Ícone para desenhar agora.
     *
     * @param model item modelo da linha (linhas de item); vazio nas outras
     */
    public static ItemStack icon(@Nullable TargetSpec spec, ItemStack model) {
        if (spec == null) {
            return INVALID;
        }
        return switch (spec.kind()) {
            case ITEM -> model.isEmpty() ? INVALID : model;
            case TAG -> cycle(TargetResolver.tagItems(spec));
            case MOD -> cycle(modItems(spec.id()));
        };
    }

    /** Nome para dicas e monitor: nome do item, ou o texto da tag / nome do mod. */
    public static Component name(@Nullable TargetSpec spec, ItemStack model) {
        if (spec == null) {
            return Component.translatable("gui.tccolonybridge.list.invalid");
        }
        return switch (spec.kind()) {
            case ITEM -> model.isEmpty() ? Component.literal(spec.text()) : model.getHoverName();
            case TAG -> Component.literal(spec.text());
            case MOD -> Component.literal(ModList.get().getModContainerById(spec.id())
                    .map(container -> container.getModInfo().getDisplayName()).orElse(spec.text()));
        };
    }

    /** Quantos itens o alvo abrange (1 para item; 0 = tag vazia / mod sem itens). */
    public static int itemCount(TargetSpec spec) {
        return switch (spec.kind()) {
            case ITEM -> 1;
            case TAG -> TargetResolver.tagItems(spec).size();
            case MOD -> modItems(spec.id()).size();
        };
    }

    private static ItemStack cycle(List<Item> items) {
        if (items.isEmpty()) {
            return INVALID;
        }
        Item item = items.get((int) ((Util.getMillis() / CYCLE_MS) % items.size()));
        return STACKS.computeIfAbsent(item, ItemStack::new);
    }

    /** Itens registrados por um mod (percorre o registro uma vez por mod e guarda). */
    private static List<Item> modItems(String mod) {
        return MOD_ITEMS.computeIfAbsent(mod, id -> {
            List<Item> items = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(id)) {
                    items.add(item);
                }
            }
            return List.copyOf(items);
        });
    }
}
