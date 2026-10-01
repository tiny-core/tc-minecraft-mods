package org.tinycore.cloud.item.policy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ItemPolicy}: modo, precedência por especificidade e BLOCK vencendo empate. */
class ItemPolicyTest {

    private static ItemRule rule(ItemRule.Scope scope, String pattern, ItemRule.Action action) {
        return new ItemRule(scope, pattern, action);
    }

    @Test
    void listaNegraSemRegrasPermiteTudo() {
        assertTrue(ItemPolicy.OPEN.evaluate("mekanism:ingot_osmium", Set.of()).allowed());
    }

    @Test
    void listaBrancaSemRegrasBloqueiaTudo() {
        ItemPolicy p = new ItemPolicy(ItemPolicy.Mode.ALLOWLIST, List.of(), 1);
        assertFalse(p.evaluate("minecraft:dirt", Set.of()).allowed());
    }

    @Test
    void bloqueiaModInteiroMasLiberaUmItem() {
        ItemPolicy p = new ItemPolicy(ItemPolicy.Mode.BLOCKLIST, List.of(
                rule(ItemRule.Scope.MOD, "refinedstorage", ItemRule.Action.BLOCK),
                rule(ItemRule.Scope.ITEM, "refinedstorage:cable", ItemRule.Action.ALLOW)), 1);
        assertFalse(p.evaluate("refinedstorage:1k_storage_disk", Set.of()).allowed());
        assertTrue(p.evaluate("refinedstorage:cable", Set.of()).allowed());
        assertTrue(p.evaluate("minecraft:dirt", Set.of()).allowed());
    }

    @Test
    void tagVenceModEModo() {
        ItemPolicy p = new ItemPolicy(ItemPolicy.Mode.ALLOWLIST, List.of(
                rule(ItemRule.Scope.MOD, "minecraft", ItemRule.Action.ALLOW),
                rule(ItemRule.Scope.TAG, "#c:shulker_boxes", ItemRule.Action.BLOCK)), 1);
        assertFalse(p.evaluate("minecraft:shulker_box", Set.of("c:shulker_boxes")).allowed());
        assertTrue(p.evaluate("minecraft:stone", Set.of()).allowed());
    }

    @Test
    void empateNoMesmoNivelBloqueia() {
        ItemPolicy p = new ItemPolicy(ItemPolicy.Mode.BLOCKLIST, List.of(
                rule(ItemRule.Scope.ITEM, "minecraft:elytra", ItemRule.Action.ALLOW),
                rule(ItemRule.Scope.ITEM, "MINECRAFT:ELYTRA", ItemRule.Action.BLOCK)), 1);
        assertFalse(p.evaluate("minecraft:elytra", Set.of()).allowed());
    }
}
