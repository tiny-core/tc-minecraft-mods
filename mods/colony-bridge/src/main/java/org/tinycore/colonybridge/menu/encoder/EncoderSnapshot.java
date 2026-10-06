package org.tinycore.colonybridge.menu.encoder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.logic.BridgeStatus;

import java.util.List;

/**
 * Foto do TC Pattern Encoder para a tela, enviada 1×/s só quando muda.
 *
 * @param lines  linhas mostradas (no máximo {@code encoderMaxLines})
 * @param hidden pedidos sem padrão que ficaram de fora do teto
 */
public record EncoderSnapshot(BridgeStatus status, String colonyName, List<EncoderLine> lines, int hidden) {

    /** Teto do codec; a config limita antes ({@code encoderMaxLines} ≤ 64). */
    public static final int MAX_LINES = 64;
    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    public static final EncoderSnapshot EMPTY = new EncoderSnapshot(BridgeStatus.STARTING, "", List.of(), 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> STATUSES[Math.floorMod(i, STATUSES.length)], BridgeStatus::ordinal),
            EncoderSnapshot::status,
            ByteBufCodecs.stringUtf8(64), EncoderSnapshot::colonyName,
            EncoderLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), EncoderSnapshot::lines,
            ByteBufCodecs.VAR_INT, EncoderSnapshot::hidden,
            EncoderSnapshot::new);

    /** true se as duas fotos mostram a mesma coisa. */
    boolean sameAs(EncoderSnapshot other) {
        if (status != other.status || hidden != other.hidden || !colonyName.equals(other.colonyName)
                || lines.size() != other.lines.size()) {
            return false;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).sameAs(other.lines.get(i))) return false;
        }
        return true;
    }
}
