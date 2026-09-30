package org.tinycore.colonybridge.logic.target;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link TargetSpec}: leitura do texto da caixa. O texto vem do cliente, então os casos inválidos importam
 * tanto quanto os válidos (nada pode passar com lixo para o NBT ou para a lógica).
 */
class TargetSpecTest {

    @Test
    void itemWithNamespace() {
        assertEquals(new TargetSpec(TargetKind.ITEM, "minecraft:iron_ingot"), TargetSpec.parse("minecraft:iron_ingot"));
    }

    @Test
    void itemWithoutNamespaceIsMinecraft() {
        assertEquals(new TargetSpec(TargetKind.ITEM, "minecraft:iron_ingot"), TargetSpec.parse("iron_ingot"));
    }

    @Test
    void tagKeepsPath() {
        assertEquals(new TargetSpec(TargetKind.TAG, "c:ingots/iron"), TargetSpec.parse("#c:ingots/iron"));
    }

    @Test
    void modId() {
        assertEquals(new TargetSpec(TargetKind.MOD, "mekanism"), TargetSpec.parse("@mekanism"));
    }

    @Test
    void spacesAndUppercaseAreNormalized() {
        assertEquals(new TargetSpec(TargetKind.TAG, "c:ingots/iron"), TargetSpec.parse("  #C:Ingots/Iron "));
    }

    @Test
    void textRoundTrip() {
        for (String text : new String[]{"minecraft:stone", "#c:ores", "@ae2"}) {
            TargetSpec spec = TargetSpec.parse(text);
            assertEquals(text, spec.text());
            assertEquals(spec, TargetSpec.parse(spec.text()));
        }
    }

    @Test
    void invalidTextIsRejected() {
        assertNull(TargetSpec.parse(null));
        assertNull(TargetSpec.parse(""));
        assertNull(TargetSpec.parse("   "));
        assertNull(TargetSpec.parse("#"));
        assertNull(TargetSpec.parse("@"));
        assertNull(TargetSpec.parse("@a"), "id de mod precisa de 2+ caracteres");
        assertNull(TargetSpec.parse("@1mod"), "id de mod começa com letra");
        assertNull(TargetSpec.parse("@my-mod"));
        assertNull(TargetSpec.parse("minecraft:"));
        assertNull(TargetSpec.parse("minecraft:iron ingot"));
        assertNull(TargetSpec.parse("a:b:c"));
    }

    @Test
    void tooLongTextIsRejected() {
        String longPath = "a".repeat(TargetSpec.MAX_TEXT);
        assertNull(TargetSpec.parse("minecraft:" + longPath));
        assertEquals(TargetKind.ITEM, TargetSpec.parse("m:" + "a".repeat(TargetSpec.MAX_TEXT - 2)).kind());
    }

    @Test
    void locationOnlyForItemAndTag() {
        assertEquals("c:ores", TargetSpec.parse("#c:ores").location().toString());
        assertNull(TargetSpec.parse("@ae2").location());
    }
}
