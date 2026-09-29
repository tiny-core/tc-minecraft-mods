package org.tinycore.colonybridge.block.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.tinycore.core.block.RedstoneMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link BridgeSettings}: o que é salvo no NBT da ponte e o que viaja nos pacotes. Protege os mundos
 * existentes (pontes antigas continuam lendo) e o servidor (pacote com número inválido não quebra nada).
 */
class BridgeSettingsTest {

    private static final BridgeSettings CUSTOM =
            new BridgeSettings(false, RedstoneMode.ACTIVE_WITH_SIGNAL, FilterMode.ALLOW, true);

    @Test
    void nbtRoundTrip() {
        assertEquals(CUSTOM, BridgeSettings.load(CUSTOM.save()));
        assertEquals(BridgeSettings.DEFAULT, BridgeSettings.load(BridgeSettings.DEFAULT.save()));
    }

    @Test
    void emptyTagFromOldBridgeGivesDefaults() {
        assertEquals(BridgeSettings.DEFAULT, BridgeSettings.load(new CompoundTag()));
    }

    @Test
    void invalidNumbersInNbtFallBackToDefaults() {
        CompoundTag tag = CUSTOM.save();
        tag.putInt("redstone", 99);
        tag.putInt("filterMode", -1);
        BridgeSettings loaded = BridgeSettings.load(tag);
        assertEquals(RedstoneMode.IGNORED, loaded.redstoneMode());
        assertEquals(FilterMode.OFF, loaded.filterMode());
    }

    @Test
    void streamCodecRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        BridgeSettings.STREAM_CODEC.encode(buf, CUSTOM);
        assertEquals(CUSTOM, BridgeSettings.STREAM_CODEC.decode(buf));
    }

    @Test
    void hostilePacketWithInvalidEnumBecomesDefault() {
        // Pacote forjado: crafting=true, redstone=77, filtro=200, exato=false.
        ByteBuf buf = Unpooled.buffer();
        buf.writeBoolean(true);
        buf.writeByte(77);  // VarInt de 1 byte
        buf.writeByte(0xC8).writeByte(0x01); // VarInt 200 (7 bits por byte)
        buf.writeBoolean(false);
        BridgeSettings decoded = BridgeSettings.STREAM_CODEC.decode(buf);
        assertEquals(RedstoneMode.IGNORED, decoded.redstoneMode());
        assertEquals(FilterMode.OFF, decoded.filterMode());
    }
}
