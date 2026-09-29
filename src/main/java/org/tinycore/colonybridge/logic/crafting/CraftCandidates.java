package org.tinycore.colonybridge.logic.crafting;

import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Escolhe <b>qual item craftar</b> para um pedido que aceita vários itens (tag, ferramenta, comida).
 * <p>
 * Passos: percorre os itens que a rede sabe craftar ({@code getCraftables}), fica com os que o pedido
 * aceita e que podem ser tentados agora (filtro, blacklist, espera após falha — decididos por quem
 * chama), aplica as regras de mod ({@link CraftRules}: só vanilla, só/exceto mods marcados) e ordena
 * pelas regras do {@link CraftOrdering} (mods preferidos, depois a {@link CraftPreference}).
 * <p>
 * Custo: a lista de craftáveis da rede (pode ter milhares de itens no ATM10) é montada no máximo uma
 * vez por ciclo e só se algum pedido precisar; o custo de cada candidato também fica em cache no ciclo.
 * Não depende do MineColonies nem da ponte: recebe os testes prontos como {@code Predicate}
 * (≈ {@code Func<T, bool>} em C#).
 */
public final class CraftCandidates {

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
            if (!CraftOrdering.modAllowed(rules, key.getModId())) {
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
        found.sort(CraftOrdering.comparator(rules, k -> k.getId().toString(), AEKey::getModId,
                k -> cost.of(crafting, k)));
        return found.get(0);
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
