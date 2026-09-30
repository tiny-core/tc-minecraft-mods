package org.tinycore.colonybridge.menu.loader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.loader.LoaderState;
import org.tinycore.core.block.RedstoneMode;

/**
 * Foto do Chunk Loader para a tela (no bloco ou pelo tablet), enviada 1×/s só quando muda.
 *
 * @param loaded          chunks carregados agora (inclui o do próprio bloco)
 * @param claimed         chunks reivindicados pela colônia na última leitura
 * @param max             máximo da config
 * @param power           consumo atual em AE/t
 * @param minutesLeft     minutos até soltar a área (só na contagem; 0 fora dela)
 */
public record ChunkLoaderSnapshot(BridgeStatus status, String colonyName, LoaderState state, boolean switchedOn,
                                  RedstoneMode redstoneMode, int loaded, int claimed, int max, double power,
                                  long minutesLeft) {

    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    public static final ChunkLoaderSnapshot EMPTY = new ChunkLoaderSnapshot(BridgeStatus.STARTING, "",
            LoaderState.OFF, true, RedstoneMode.IGNORED, 0, 0, 0, 0, 0);

    /** Mais de 6 campos: codec escrito à mão (o {@code composite} do Minecraft vai só até 6). */
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkLoaderSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeVarInt(s.status.ordinal());
                ByteBufCodecs.stringUtf8(64).encode(buf, s.colonyName);
                buf.writeVarInt(s.state.ordinal());
                buf.writeBoolean(s.switchedOn);
                buf.writeVarInt(s.redstoneMode.ordinal());
                buf.writeVarInt(s.loaded);
                buf.writeVarInt(s.claimed);
                buf.writeVarInt(s.max);
                buf.writeDouble(s.power);
                buf.writeVarLong(s.minutesLeft);
            },
            buf -> new ChunkLoaderSnapshot(STATUSES[Math.floorMod(buf.readVarInt(), STATUSES.length)],
                    ByteBufCodecs.stringUtf8(64).decode(buf), LoaderState.byId(buf.readVarInt()), buf.readBoolean(),
                    RedstoneMode.byId(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readVarLong()));
}
