package org.tinycore.cloud.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.tinycore.cloud.cloud.CloudQuota;

/**
 * Uso da cota do canal mostrado no topo da tela do TC Cloud Link (servidor → cliente): o que o canal já tem e os
 * limites que o dono da nuvem definiu no painel do TCMine. Só números; a conta da barra é do {@link CloudQuota}.
 *
 * @param known false sem sessão aberta (nuvem desligada, conectando...): a tela não mostra a barra
 */
public record LinkQuota(boolean known, int types, int maxTypes, long total, long maxTotal) {

    public static final LinkQuota NONE = new LinkQuota(false, 0, 0, 0, 0);

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkQuota> STREAM_CODEC = StreamCodec.of(
            (buf, q) -> {
                buf.writeBoolean(q.known);
                buf.writeVarInt(q.types);
                buf.writeVarInt(q.maxTypes);
                buf.writeVarLong(q.total);
                buf.writeVarLong(q.maxTotal);
            },
            buf -> new LinkQuota(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                    buf.readVarLong()));

    /** Os limites como {@link CloudQuota} (para {@code fill}, {@code limitsTypes}...). */
    public @NotNull CloudQuota limits() {
        return new CloudQuota(Math.max(0, maxTypes), Math.max(0, maxTotal));
    }

    /** Ocupação de 0 a 1 (o mais apertado entre tipos e total). */
    public double fill() {
        return limits().fill(types, total);
    }
}
