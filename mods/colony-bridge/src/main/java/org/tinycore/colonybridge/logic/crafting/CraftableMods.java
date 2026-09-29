package org.tinycore.colonybridge.logic.crafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import java.util.List;
import java.util.TreeSet;

/**
 * Lista os mods que têm algum item craftável na rede ME, para a aba "Mods" da tela da ponte mostrar
 * opções reais (o jogador marca em vez de digitar ids). Limitada a {@link #MAX} mods para o pacote
 * enviado ao cliente não crescer sem limite.
 */
public final class CraftableMods {

    /** Teto de mods sincronizados com a tela. */
    public static final int MAX = 64;

    private CraftableMods() {}

    /** Mods em ordem alfabética (o {@code TreeSet} ≈ {@code SortedSet<T>} em C#). */
    public static List<String> of(ICraftingService crafting) {
        TreeSet<String> mods = new TreeSet<>();
        for (AEKey key : crafting.getCraftables(k -> k instanceof AEItemKey)) {
            mods.add(key.getModId());
        }
        return mods.stream().limit(MAX).toList();
    }
}
