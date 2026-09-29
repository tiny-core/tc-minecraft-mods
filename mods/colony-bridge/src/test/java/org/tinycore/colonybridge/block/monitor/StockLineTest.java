package org.tinycore.colonybridge.block.monitor;

import org.junit.jupiter.api.Test;
import org.tinycore.colonybridge.stats.SupplySummary;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link StockLine} e {@link SupplyContent}: a cor de cada linha do Abastecedor no monitor e o número
 * "abaixo do alvo". O item não importa para essas regras, então os testes usam null (sem carregar o jogo).
 */
class StockLineTest {

    private static StockLine keep(int target, long current) {
        return new StockLine(null, true, target, current);
    }

    private static StockLine surplus(int target, long current) {
        return new StockLine(null, false, target, current);
    }

    @Test
    void keepLineIsBelowUntilItReachesTheTarget() {
        assertEquals(StockLine.State.BELOW, keep(64, 10).state());
        assertEquals(StockLine.State.OK, keep(64, 64).state());
        assertEquals(StockLine.State.OK, keep(64, 200).state(), "manter: ter a mais não é problema");
    }

    @Test
    void surplusLineIsAboveWhenItExceedsTheLimit() {
        assertEquals(StockLine.State.ABOVE, surplus(128, 300).state());
        assertEquals(StockLine.State.OK, surplus(128, 128).state());
        assertEquals(StockLine.State.OK, surplus(128, 0).state(), "excedente: ter a menos não é problema");
    }

    @Test
    void belowTargetCountsOnlyKeepLinesMissingItems() {
        SupplyContent content = new SupplyContent(SupplySummary.EMPTY,
                List.of(keep(64, 10), keep(10, 10), surplus(5, 100), keep(3, 0)));
        assertEquals(2, content.belowTarget());
    }
}
