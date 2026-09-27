package org.tinycore.colonybridge.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.block.BridgeSettings;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.CycleReport;
import org.tinycore.colonybridge.logic.RequestLine;

import java.util.List;

/**
 * Foto do estado da ponte enviada à tela: só dados prontos para exibir, nada que o cliente
 * possa usar para decidir algo no servidor.
 * <p>
 * Montada no servidor por {@code ColonyBridgeBlockEntity.snapshot()} e enviada pelo
 * {@link ColonyBridgeMenu} só quando muda. Os limites do codec (tamanho do nome e da lista)
 * protegem contra pacotes gigantes.
 *
 * @param totalRequests total de pedidos em aberto (a lista pode estar cortada em {@link CycleReport#MAX_LINES})
 */
public record BridgeSnapshot(BridgeStatus status, String colonyName, BridgeSettings settings,
                             List<RequestLine> lines, int totalRequests) {

    public static final BridgeSnapshot EMPTY =
            new BridgeSnapshot(BridgeStatus.STARTING, "", BridgeSettings.DEFAULT, List.of(), 0);

    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> STATUSES[Math.floorMod(i, STATUSES.length)], BridgeStatus::ordinal),
            BridgeSnapshot::status,
            ByteBufCodecs.stringUtf8(64), BridgeSnapshot::colonyName,
            BridgeSettings.STREAM_CODEC, BridgeSnapshot::settings,
            RequestLine.STREAM_CODEC.apply(ByteBufCodecs.list(CycleReport.MAX_LINES)), BridgeSnapshot::lines,
            ByteBufCodecs.VAR_INT, BridgeSnapshot::totalRequests,
            BridgeSnapshot::new);

    /** Igualdade de conteúdo (as linhas têm ItemStack, que não tem equals por valor). */
    public boolean sameAs(BridgeSnapshot other) {
        if (status != other.status || !settings.equals(other.settings) || totalRequests != other.totalRequests
                || !colonyName.equals(other.colonyName) || lines.size() != other.lines.size()) {
            return false;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).sameAs(other.lines.get(i))) {
                return false;
            }
        }
        return true;
    }
}
