package org.tinycore.colonybridge.menu.supply;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.logic.BridgeStatus;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.core.block.RedstoneMode;

import java.util.List;

/**
 * Foto do Abastecedor enviada à tela: estado, colônia, modo de redstone, auto-craft ligado e, para cada linha das duas listas,
 * quanto há no armazém e a situação ({@link SupplyLineStat}).
 * <p>
 * As linhas em si (alvo, quantidade) não entram aqui: vão no {@code TargetListPayload}, só quando a lista muda.
 * As listas do pacote têm teto ({@link TargetList#HARD_MAX_LINES}) para um pacote hostil não pedir memória sem fim.
 */
public record SupplySnapshot(BridgeStatus status, String colonyName, RedstoneMode redstoneMode, boolean craftMissing,
                             List<SupplyLineStat> keep, List<SupplyLineStat> surplus) {

    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    public static final SupplySnapshot EMPTY = new SupplySnapshot(BridgeStatus.STARTING, "",
            RedstoneMode.IGNORED, true, List.of(), List.of());

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplySnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> STATUSES[Math.floorMod(i, STATUSES.length)], BridgeStatus::ordinal),
            SupplySnapshot::status,
            ByteBufCodecs.stringUtf8(64), SupplySnapshot::colonyName,
            ByteBufCodecs.idMapper(RedstoneMode::byId, RedstoneMode::ordinal), SupplySnapshot::redstoneMode,
            ByteBufCodecs.BOOL, SupplySnapshot::craftMissing,
            SupplyLineStat.STREAM_CODEC.apply(ByteBufCodecs.list(TargetList.HARD_MAX_LINES)), SupplySnapshot::keep,
            SupplyLineStat.STREAM_CODEC.apply(ByteBufCodecs.list(TargetList.HARD_MAX_LINES)), SupplySnapshot::surplus,
            SupplySnapshot::new);

    /** Dados da linha {@code index} da lista "manter" ({@code keep = true}) ou "excedente". */
    public SupplyLineStat line(boolean keepList, int index) {
        List<SupplyLineStat> lines = keepList ? keep : surplus;
        return index >= 0 && index < lines.size() ? lines.get(index) : SupplyLineStat.EMPTY;
    }

    public SupplySnapshot withRedstone(RedstoneMode mode) {
        return new SupplySnapshot(status, colonyName, mode, craftMissing, keep, surplus);
    }

    public SupplySnapshot withCraftMissing(boolean value) {
        return new SupplySnapshot(status, colonyName, redstoneMode, value, keep, surplus);
    }
}
