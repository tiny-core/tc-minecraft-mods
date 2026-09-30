package org.tinycore.colonybridge.block.monitor;

import org.junit.jupiter.api.Test;
import org.tinycore.colonybridge.logic.supply.SupplyLineStatus;
import org.tinycore.colonybridge.stats.SupplySummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SupplyContent}: o número "precisam de atenção" do painel do Abastecedor. O item não importa para a
 * regra, então os testes usam null (sem carregar o jogo). A situação de cada linha é testada em
 * {@code SupplyLineStatusTest}.
 */
class StockLineTest {

    private static StockLine line(SupplyLineStatus status) {
        return new StockLine(null, null, true, 64, false, 0, 0, status);
    }

    @Test
    void attentionCountsWarningsAndProblemsOnly() {
        SupplyContent content = new SupplyContent(SupplySummary.EMPTY, List.of(
                line(SupplyLineStatus.STOCKED), line(SupplyLineStatus.RESTOCKING), line(SupplyLineStatus.NETWORK_EMPTY),
                line(SupplyLineStatus.HELD_REQUESTED), line(SupplyLineStatus.UNKNOWN)), -1);
        assertEquals(2, content.needingAttention());
    }
}
