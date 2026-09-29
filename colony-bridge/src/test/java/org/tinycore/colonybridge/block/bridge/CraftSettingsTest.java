package org.tinycore.colonybridge.block.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.Test;
import org.tinycore.colonybridge.logic.crafting.CraftPreference;
import org.tinycore.colonybridge.logic.crafting.ModFilterMode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link CraftSettings}: tudo que vem do cliente ou do NBT passa por {@code sanitized}, então é aqui que
 * pacotes hostis (ids inválidos, listas enormes) precisam ser barrados.
 */
class CraftSettingsTest {

    @Test
    void sanitizedDropsInvalidIdsAndDuplicates() {
        CraftSettings s = CraftSettings.sanitized(null, ModFilterMode.ONLY,
                List.of("minecraft", "Minecraft", "mekanism", "../hack", "", "minecraft", "a".repeat(65)));
        assertEquals(List.of("minecraft", "mekanism"), s.mods());
    }

    @Test
    void sanitizedCapsTheListSize() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            many.add("mod" + i);
        }
        assertEquals(64, CraftSettings.sanitized(null, ModFilterMode.ALL, many).mods().size());
    }

    @Test
    void toggleModAddsThenRemoves() {
        CraftSettings s = CraftSettings.DEFAULT.toggleMod("minecraft");
        assertEquals(List.of("minecraft"), s.mods());
        assertEquals(List.of(), s.toggleMod("minecraft").mods());
    }

    @Test
    void preferenceButtonCyclesThroughServerDefault() {
        CraftSettings s = CraftSettings.DEFAULT; // null = padrão do servidor
        assertEquals(CraftPreference.CHEAPEST, s.nextPreference());
        s = s.withPreference(CraftPreference.LIST);
        assertNull(s.nextPreference(), "depois da última opção volta para o padrão do servidor");
    }

    @Test
    void nbtRoundTripKeepsEverything() {
        CraftSettings original = CraftSettings.sanitized(CraftPreference.MOST_EXPENSIVE, ModFilterMode.PREFER,
                List.of("minecraft", "ae2"));
        assertEquals(original, CraftSettings.load(original.save()));
    }

    @Test
    void oldBridgesWithoutFieldsLoadAsDefault() {
        assertEquals(CraftSettings.DEFAULT, CraftSettings.load(new CompoundTag()));
    }

    @Test
    void networkRoundTrip() {
        CraftSettings original = CraftSettings.sanitized(CraftPreference.CHEAPEST, ModFilterMode.EXCEPT,
                List.of("minecraft", "mekanism"));
        ByteBuf buf = Unpooled.buffer();
        CraftSettings.STREAM_CODEC.encode(buf, original);
        assertEquals(original, CraftSettings.STREAM_CODEC.decode(buf));
    }

    /** Pacote montado "à mão" por um cliente modificado: valores fora do lugar viram algo seguro. */
    @Test
    void hostilePacketIsCleanedOnDecode() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufCodecs.VAR_INT.encode(buf, 999);   // preferência inexistente
        ByteBufCodecs.VAR_INT.encode(buf, -5);    // modo de mods inexistente
        ByteBufCodecs.VAR_INT.encode(buf, 2);     // 2 mods
        ByteBufCodecs.STRING_UTF8.encode(buf, "../../etc");
        ByteBufCodecs.STRING_UTF8.encode(buf, "ae2");
        CraftSettings decoded = CraftSettings.STREAM_CODEC.decode(buf);
        assertNull(decoded.preference(), "preferência inválida vira padrão do servidor");
        assertEquals(ModFilterMode.ALL, decoded.modMode());
        assertEquals(List.of("ae2"), decoded.mods());
    }
}
