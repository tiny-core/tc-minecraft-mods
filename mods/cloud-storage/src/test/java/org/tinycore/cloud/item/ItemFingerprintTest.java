package org.tinycore.cloud.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** {@link ItemFingerprint}/{@link CanonicalNbt}: mesma informação = mesma impressão digital, sempre. */
class ItemFingerprintTest {

    private static CompoundTag item(String... keyValues) {
        CompoundTag components = new CompoundTag();
        for (int i = 0; i < keyValues.length; i += 2) components.putString(keyValues[i], keyValues[i + 1]);
        CompoundTag root = new CompoundTag();
        root.putString("id", "minecraft:diamond_sword");
        root.put("components", components);
        return root;
    }

    @Test
    void ordemDeInsercaoNaoMudaAImpressao() {
        // Muitas chaves para o HashMap do CompoundTag realmente embaralhar a ordem.
        String[] forward = new String[40];
        String[] backward = new String[40];
        for (int i = 0; i < 20; i++) {
            forward[2 * i] = "k" + i;
            forward[2 * i + 1] = "v" + i;
            backward[2 * (19 - i)] = "k" + i;
            backward[2 * (19 - i) + 1] = "v" + i;
        }
        assertEquals(ItemFingerprint.of(item(forward)), ItemFingerprint.of(item(backward)));
    }

    @Test
    void qualquerDiferencaMudaAImpressao() {
        assertNotEquals(ItemFingerprint.of(item("a", "1")), ItemFingerprint.of(item("a", "2")));
        assertNotEquals(ItemFingerprint.of(item("a", "1")), ItemFingerprint.of(item("b", "1")));
    }

    @Test
    void ordemDaListaImporta() {
        ListTag ab = new ListTag();
        ab.add(StringTag.valueOf("a"));
        ab.add(StringTag.valueOf("b"));
        ListTag ba = new ListTag();
        ba.add(StringTag.valueOf("b"));
        ba.add(StringTag.valueOf("a"));
        assertNotEquals(ItemFingerprint.of(ab), ItemFingerprint.of(ba), "lista tem ordem com significado");
    }

    @Test
    void formatoHexDe64Caracteres() {
        assertEquals(64, ItemFingerprint.of(item()).length());
    }
}
