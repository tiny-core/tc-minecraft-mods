package org.tinycore.colonybridge.logic.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CraftOrdering}: qual item a ponte crafta para um pedido por tag. Os candidatos aqui são ids de
 * item em texto ("mod:item"), com custo inventado; no jogo são {@code AEItemKey}.
 */
class CraftOrderingTest {

    private static final Map<String, Double> COST = Map.of(
            "minecraft:stone_pickaxe", 5.0,
            "minecraft:iron_pickaxe", 20.0,
            "minecraft:diamond_pickaxe", 300.0,
            "mekanism:steel_pickaxe", 40.0,
            "ae2:certus_pickaxe", 10.0);

    private static List<String> sorted(CraftRules rules) {
        List<String> list = new ArrayList<>(COST.keySet());
        list.removeIf(id -> !CraftOrdering.modAllowed(rules, mod(id)));
        list.sort(CraftOrdering.comparator(rules, id -> id, CraftOrderingTest::mod, COST::get));
        return list;
    }

    private static String mod(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    private static CraftRules rules(CraftPreference preference, List<String> preferred, ModFilterMode mode,
                                    Set<String> mods, boolean vanillaOnly) {
        return new CraftRules(preference, preferred, mode, mods, vanillaOnly);
    }

    @Test
    void cheapestFirst() {
        assertEquals("minecraft:stone_pickaxe",
                sorted(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.ALL, Set.of(), false)).get(0));
    }

    @Test
    void mostExpensiveFirst() {
        assertEquals("minecraft:diamond_pickaxe",
                sorted(rules(CraftPreference.MOST_EXPENSIVE, List.of(), ModFilterMode.ALL, Set.of(), false)).get(0));
    }

    @Test
    void listOrderWinsAndUnlistedFollowCheapestFirst() {
        List<String> result = sorted(rules(CraftPreference.LIST,
                List.of("minecraft:iron_pickaxe", "mekanism:steel_pickaxe"), ModFilterMode.ALL, Set.of(), false));
        assertEquals(List.of("minecraft:iron_pickaxe", "mekanism:steel_pickaxe",
                "minecraft:stone_pickaxe", "ae2:certus_pickaxe", "minecraft:diamond_pickaxe"), result);
    }

    @Test
    void preferMarkedModsComesBeforeThePreference() {
        List<String> result = sorted(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.PREFER,
                Set.of("mekanism"), false));
        assertEquals("mekanism:steel_pickaxe", result.get(0), "mod marcado primeiro, mesmo mais caro");
        assertEquals("minecraft:stone_pickaxe", result.get(1), "depois, o mais barato dos demais");
        assertEquals(5, result.size(), "PREFER não exclui ninguém");
    }

    @Test
    void onlyAndExceptFilterMods() {
        assertEquals(List.of("ae2:certus_pickaxe"),
                sorted(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.ONLY, Set.of("ae2"), false)));
        assertFalse(sorted(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.EXCEPT, Set.of("minecraft"), false))
                .stream().anyMatch(id -> id.startsWith("minecraft:")));
    }

    @Test
    void serverVanillaOnlyBeatsBridgeSettings() {
        // A ponte pede "só mekanism", mas o servidor exige vanilla: nada serve.
        assertTrue(sorted(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.ONLY, Set.of("mekanism"), true))
                .isEmpty());
        assertTrue(sorted(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.ALL, Set.of(), true))
                .stream().allMatch(id -> id.startsWith("minecraft:")));
    }

    @Test
    void equalCostIsBrokenByIdSoTheChoiceIsStable() {
        Map<String, Double> tie = Map.of("minecraft:b", 1.0, "minecraft:a", 1.0);
        List<String> list = new ArrayList<>(tie.keySet());
        list.sort(CraftOrdering.comparator(rules(CraftPreference.CHEAPEST, List.of(), ModFilterMode.ALL, Set.of(), false),
                id -> id, CraftOrderingTest::mod, tie::get));
        assertEquals(List.of("minecraft:a", "minecraft:b"), list);
    }
}
