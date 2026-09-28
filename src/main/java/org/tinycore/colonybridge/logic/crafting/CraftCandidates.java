package org.tinycore.colonybridge.logic.crafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Escolhe <b>qual item craftar</b> para um pedido que aceita vários itens (tag, ferramenta, comida).
 * <p>
 * Passos: percorre os itens que a rede sabe craftar ({@code getCraftables}), fica com os que o pedido
 * aceita e que podem ser tentados agora (filtro, blacklist, espera após falha — decididos por quem
 * chama), aplica as regras de mod ({@link CraftRules}: só vanilla, só/exceto mods marcados) e ordena:
 * mods preferidos primeiro (modo {@link ModFilterMode#PREFER}), depois a {@link CraftPreference}.
 * <p>
 * Custo: a lista de craftáveis da rede (pode ter milhares de itens no ATM10) é montada no máximo uma
 * vez por ciclo e só se algum pedido precisar; o custo de cada candidato também fica em cache no ciclo.
 * Não depende do MineColonies nem da ponte: recebe os testes prontos como {@code Predicate}
 * (≈ {@code Func<T, bool>} em C#).
 */
public final class CraftCandidates {

    private static final String VANILLA = "minecraft";

    private final CraftCost cost = new CraftCost();
    private @Nullable List<AEItemKey> craftables;

    /** Descarta os caches do ciclo anterior. Chamar no começo de cada ciclo. */
    public void beginCycle() {
        craftables = null;
        cost.clear();
    }

    /**
     * @param accepts o pedido aceita este item (e o filtro da ponte deixa)
     * @param usable  dá para tentar craftar este item agora (não está em espera, blacklist...)
     * @return o melhor candidato segundo a preferência configurada, ou null se nenhum serve
     */
    public @Nullable AEItemKey choose(ICraftingService crafting, CraftRules rules, Predicate<ItemStack> accepts,
                                      Predicate<AEItemKey> usable) {
        int max = Config.TAG_CRAFT_MAX_CANDIDATES.get();
        List<AEItemKey> found = new ArrayList<>();
        for (AEItemKey key : craftables(crafting)) {
            String mod = key.getModId();
            if ((rules.vanillaOnly() && !VANILLA.equals(mod)) || !rules.modMode().allows(mod, rules.mods())) {
                continue;
            }
            // getReadOnlyStack(): stack em cache na própria chave, sem alocar; só leitura.
            if (accepts.test(key.getReadOnlyStack()) && usable.test(key)) {
                found.add(key);
                if (found.size() >= max) {
                    break;
                }
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        found.sort(order(crafting, rules));
        return found.get(0);
    }

    /**
     * Ordem dos candidatos: mods preferidos primeiro (se o modo for PREFER), depois a preferência
     * configurada, com o id do item como desempate estável.
     */
    private Comparator<AEItemKey> order(ICraftingService crafting, CraftRules rules) {
        Comparator<AEItemKey> cheapest = Comparator.comparingDouble(k -> cost.of(crafting, k));
        Comparator<AEItemKey> byPreference = switch (rules.preference()) {
            case CHEAPEST -> cheapest;
            case MOST_EXPENSIVE -> cheapest.reversed();
            case LIST -> Comparator.<AEItemKey>comparingInt(k -> listIndex(rules, k)).thenComparing(cheapest);
        };
        if (rules.modMode() == ModFilterMode.PREFER) {
            // false vem antes de true: itens de mods marcados (não "fora da lista") primeiro.
            byPreference = Comparator.<AEItemKey, Boolean>comparing(k -> !rules.mods().contains(k.getModId()))
                    .thenComparing(byPreference);
        }
        return byPreference.thenComparing(k -> k.getId().toString());
    }

    /** Posição do item na lista de preferidos; fora da lista vai para o fim. */
    private static int listIndex(CraftRules rules, AEItemKey key) {
        int index = rules.preferredIds().indexOf(key.getId().toString());
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /** Itens craftáveis da rede, montados uma vez por ciclo. */
    private List<AEItemKey> craftables(ICraftingService crafting) {
        if (craftables == null) {
            craftables = new ArrayList<>();
            for (AEKey key : crafting.getCraftables(k -> k instanceof AEItemKey)) {
                craftables.add((AEItemKey) key);
            }
        }
        return craftables;
    }
}
