package org.tinycore.colonybridge.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.tinycore.colonybridge.block.RedstoneMode;
import org.tinycore.colonybridge.block.supply.StockList;
import org.tinycore.colonybridge.logic.BridgeStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Foto do bloco de abastecimento enviada à tela: estado, colônia, modo de redstone e, para cada linha,
 * a quantidade alvo e quanto existe no armazém.
 * <p>
 * Os itens das linhas não entram aqui: eles são slots do menu e o Minecraft já os sincroniza sozinho.
 */
public record SupplySnapshot(BridgeStatus status, String colonyName, RedstoneMode redstoneMode,
                             List<Integer> amounts, List<Long> counts) {

    private static final BridgeStatus[] STATUSES = BridgeStatus.values();

    public static final SupplySnapshot EMPTY = new SupplySnapshot(BridgeStatus.STARTING, "",
            RedstoneMode.IGNORED, Collections.nCopies(StockList.SIZE, 0),
            Collections.nCopies(StockList.SIZE, 0L));

    public static final StreamCodec<RegistryFriendlyByteBuf, SupplySnapshot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> STATUSES[Math.floorMod(i, STATUSES.length)], BridgeStatus::ordinal),
            SupplySnapshot::status,
            ByteBufCodecs.stringUtf8(64), SupplySnapshot::colonyName,
            ByteBufCodecs.idMapper(RedstoneMode::byId, RedstoneMode::ordinal), SupplySnapshot::redstoneMode,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(StockList.SIZE)), SupplySnapshot::amounts,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(StockList.SIZE)), SupplySnapshot::counts,
            SupplySnapshot::new);

    /** Quantidade alvo da linha, ou 0 se o pacote veio mais curto (versões diferentes). */
    public int amount(int slot) {
        return slot < amounts.size() ? amounts.get(slot) : 0;
    }

    public long count(int slot) {
        return slot < counts.size() ? counts.get(slot) : 0;
    }

    /** Cópia com uma quantidade trocada: a tela mostra a mudança na hora, antes da resposta do servidor. */
    public SupplySnapshot withAmount(int slot, int amount) {
        List<Integer> copy = new ArrayList<>(amounts);
        while (copy.size() < StockList.SIZE) {
            copy.add(0);
        }
        copy.set(slot, amount);
        return new SupplySnapshot(status, colonyName, redstoneMode, List.copyOf(copy), counts);
    }

    public SupplySnapshot withRedstone(RedstoneMode mode) {
        return new SupplySnapshot(status, colonyName, mode, amounts, counts);
    }
}
