package org.tinycore.cloud.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link WorldReferenceDetector}: sinais de conteúdo guardado no mundo. */
class WorldReferenceDetectorTest {

    private final WorldReferenceDetector detector = WorldReferenceDetector.withDefaults();

    @Test
    void itemComumNaoEhSuspeito() {
        CompoundTag components = new CompoundTag();
        components.putInt("minecraft:damage", 12);
        CompoundTag name = new CompoundTag();
        name.putString("text", "Espada do Jocian");
        components.put("minecraft:custom_name", name);
        assertFalse(detector.find(components).isPresent());
    }

    @Test
    void uuidEmIntArrayProfundo() {
        CompoundTag inner = new CompoundTag();
        inner.put("ref", new IntArrayTag(new int[]{1, 2, 3, 4}));
        ListTag list = new ListTag();
        list.add(inner);
        CompoundTag data = new CompoundTag();
        data.put("entries", list);
        CompoundTag components = new CompoundTag();
        components.put("somemod:data", data);
        assertTrue(detector.find(components).orElseThrow().contains("int[4]"));
    }

    @Test
    void uuidEmTexto() {
        CompoundTag components = new CompoundTag();
        components.putString("somemod:link", "disk-" + UUID.randomUUID());
        assertTrue(detector.find(components).isPresent());
    }

    @Test
    void nomeDeChaveSuspeito() {
        CompoundTag components = new CompoundTag();
        components.putInt("sophisticatedbackpacks:storage_uuid", 0);
        assertTrue(detector.find(components).isPresent());
        CompoundTag freq = new CompoundTag();
        freq.putInt("Frequency", 5);
        CompoundTag c2 = new CompoundTag();
        c2.put("enderstorage:data", freq);
        assertTrue(detector.find(c2).isPresent(), "comparação sem diferenciar maiúsculas");
    }

    @Test
    void perfilDeCabecaDeJogadorEhIgnorado() {
        CompoundTag profile = new CompoundTag();
        profile.put("id", new IntArrayTag(new int[]{1, 2, 3, 4}));
        CompoundTag components = new CompoundTag();
        components.put("minecraft:profile", profile);
        assertFalse(detector.find(components).isPresent());
    }
}
