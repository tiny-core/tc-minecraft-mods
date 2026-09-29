package org.tinycore.colonybridge.menu.bridge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.block.bridge.BridgeSettings;
import org.tinycore.colonybridge.block.bridge.CraftSettings;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.bridge.RequestCounts;
import org.tinycore.colonybridge.logic.crafting.CraftableMods;

import java.util.List;

/**
 * Foto do estado da ponte enviada à tela: só dados prontos para exibir, nada que o cliente
 * possa usar para decidir algo no servidor.
 * <p>
 * Montada no servidor por {@code ColonyBridgeBlockEntity.snapshot()} e enviada pelo
 * {@link ColonyBridgeMenu} só quando muda. A lista de pedidos e as estatísticas não vêm aqui: ficam nos
 * monitores. Os limites do codec (tamanho do nome e da lista de mods) protegem contra pacotes gigantes.
 * Todos os campos comparam por valor, então {@code equals} do record basta para saber se mudou.
 *
 * @param counts        resumo do último ciclo (aba "Geral")
 * @param craftableMods mods com item craftável na rede (aba "Mods"), no máximo {@link CraftableMods#MAX}
 */
public record BridgeSnapshot(BridgeStatus status, String colonyName, BridgeSettings settings,
                             CraftSettings craftSettings, RequestCounts counts, List<String> craftableMods) {

    public static final BridgeSnapshot EMPTY = new BridgeSnapshot(BridgeStatus.STARTING, "",
            BridgeSettings.DEFAULT, CraftSettings.DEFAULT, RequestCounts.EMPTY, List.of());

    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> STATUSES[Math.floorMod(i, STATUSES.length)], BridgeStatus::ordinal),
            BridgeSnapshot::status,
            ByteBufCodecs.stringUtf8(64), BridgeSnapshot::colonyName,
            BridgeSettings.STREAM_CODEC, BridgeSnapshot::settings,
            CraftSettings.STREAM_CODEC, BridgeSnapshot::craftSettings,
            RequestCounts.STREAM_CODEC, BridgeSnapshot::counts,
            ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(CraftableMods.MAX)), BridgeSnapshot::craftableMods,
            BridgeSnapshot::new);
}
