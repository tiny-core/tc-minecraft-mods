package org.tinycore.cloud.cloud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PlayerCloudSession}: contrato estilo AE2 (simular/aplicar), cota, canal e lotes numerados. */
class PlayerCloudSessionTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID CHANNEL = UUID.randomUUID();
    private static final BalanceKey IRON = new BalanceKey(CHANNEL, "iron");
    private static final BalanceKey GOLD = new BalanceKey(CHANNEL, "gold");

    private static PlayerCloudSession session(Map<BalanceKey, Long> snapshot, CloudQuota quota) {
        return new PlayerCloudSession(PLAYER, 7, 0, Set.of(CHANNEL), snapshot, quota);
    }

    @Test
    void simularNaoAlteraNada() {
        PlayerCloudSession s = session(Map.of(IRON, 100L), CloudQuota.UNLIMITED);
        assertEquals(40, s.extract(IRON, 40, true));
        assertEquals(64, s.insert(GOLD, 64, true));
        assertEquals(100, s.available(IRON));
        assertEquals(0, s.available(GOLD));
        assertTrue(s.isSettled());
    }

    @Test
    void naoRetiraMaisQueOSaldo() {
        PlayerCloudSession s = session(Map.of(IRON, 10L), CloudQuota.UNLIMITED);
        assertEquals(10, s.extract(IRON, 64, false));
        assertEquals(0, s.extract(IRON, 1, false));
    }

    @Test
    void canalDesconhecidoNaoAceitaNada() {
        PlayerCloudSession s = session(Map.of(), CloudQuota.UNLIMITED);
        assertEquals(0, s.insert(new BalanceKey(UUID.randomUUID(), "iron"), 10, false));
    }

    @Test
    void cotaDeTiposETotal() {
        PlayerCloudSession s = session(Map.of(IRON, 90L), new CloudQuota(1, 100));
        assertEquals(0, s.insert(GOLD, 5, false), "tipo novo além do limite");
        assertEquals(10, s.insert(IRON, 50, false), "só cabem 10 até o total 100");
    }

    @Test
    void somenteLeituraBloqueiaEntradaESaida() {
        PlayerCloudSession s = session(Map.of(IRON, 10L), CloudQuota.UNLIMITED);
        s.setReadOnly(true);
        assertEquals(0, s.extract(IRON, 1, false));
        assertEquals(0, s.insert(IRON, 1, false));
        assertEquals(10, s.available(IRON), "continua vendo o saldo");
    }

    @Test
    void lotesNumeradosComSaldoEsperado() {
        PlayerCloudSession s = session(Map.of(IRON, 100L), CloudQuota.UNLIMITED);
        s.extract(IRON, 30, false);
        List<Batch> tick = s.endOfTick();
        assertEquals(1, tick.size());
        Batch b = tick.getFirst();
        assertEquals(7, b.epoch());
        assertEquals(1, b.seq());
        assertEquals(70L, b.expected().get(IRON));

        s.insert(GOLD, 5, false);
        assertTrue(s.endOfTick().isEmpty(), "crédito não sai no fim do tick");
        List<Batch> flushed = s.onFlushedSave();
        assertEquals(1, flushed.size());
        assertEquals(2, flushed.getFirst().seq());
        assertEquals(5L, flushed.getFirst().expected().get(GOLD));
        assertTrue(s.isSettled());
    }

    @Test
    void saveSeparaDebitosDeCreditos() {
        PlayerCloudSession s = session(Map.of(IRON, 100L), CloudQuota.UNLIMITED);
        s.insert(GOLD, 5, false);
        s.onWorldSave();          // crédito passa a aguardar IO
        s.extract(IRON, 1, false); // débito ainda não drenado quando o save chega
        List<Batch> batches = s.onWorldSave();
        assertEquals(2, batches.size());
        assertTrue(batches.get(0).ops().getFirst().isDebit(), "débitos primeiro");
        assertEquals(5, batches.get(1).ops().getFirst().delta());
    }
}
