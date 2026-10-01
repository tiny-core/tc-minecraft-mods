package org.tinycore.cloud.cloud;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PendingChanges}: "débito cedo, crédito tarde" e o netting assimétrico. Cada teste descreve uma
 * sequência de jogo e o que pode virar durável em cada momento.
 */
class PendingChangesTest {

    private static final BalanceKey DIAMOND = new BalanceKey(UUID.randomUUID(), "diamond");

    @Test
    void debitoViraDuravelNoFimDoTick() {
        PendingChanges p = new PendingChanges();
        p.debit(DIAMOND, 10);
        assertEquals(List.of(new CloudOp(DIAMOND, -10)), p.drainTickDebits());
        assertTrue(p.drainTickDebits().isEmpty(), "já drenado");
    }

    @Test
    void creditoSoViraDuravelNoSegundoAutosave() {
        PendingChanges p = new PendingChanges();
        p.credit(DIAMOND, 64);
        assertTrue(p.drainTickDebits().isEmpty());
        assertTrue(p.onWorldSave().isEmpty(), "1º save: o mundo sem o item ainda está sendo gravado");
        assertEquals(List.of(new CloudOp(DIAMOND, 64)), p.onWorldSave(), "2º save: o IO do 1º já terminou");
        assertTrue(p.isEmpty());
    }

    @Test
    void saveComFlushTornaTodoCreditoDuravelNaHora() {
        PendingChanges p = new PendingChanges();
        p.credit(DIAMOND, 10);
        p.onWorldSave();
        p.credit(DIAMOND, 5);
        assertEquals(List.of(new CloudOp(DIAMOND, 15)), p.onFlushedSave(), "aguardando IO + abertos, somados");
        assertTrue(p.isEmpty());
    }

    @Test
    void debitoConsomeCreditoPendenteAntes() {
        PendingChanges p = new PendingChanges();
        p.credit(DIAMOND, 64);
        p.debit(DIAMOND, 70);
        assertEquals(List.of(new CloudOp(DIAMOND, -6)), p.drainTickDebits(), "só o excedente vira débito");
        assertEquals(0, p.pendingCredit(DIAMOND));
    }

    @Test
    void debitoConsomeTambemCreditoAguardandoIo() {
        PendingChanges p = new PendingChanges();
        p.credit(DIAMOND, 20);
        p.onWorldSave(); // 20 aguardando IO
        p.credit(DIAMOND, 5); // 5 abertos
        p.debit(DIAMOND, 22);
        assertTrue(p.drainTickDebits().isEmpty(), "coberto pelos créditos pendentes");
        assertEquals(3, p.pendingCredit(DIAMOND));
    }

    @Test
    void creditoNaoAnulaDebitoDoMesmoTick() {
        // O chunk com o item retirado pode ter ido para a fila de gravação entre as duas operações:
        // anular aqui duplicaria depois de um crash. O débito fica durável e o crédito espera.
        PendingChanges p = new PendingChanges();
        p.debit(DIAMOND, 10);
        p.credit(DIAMOND, 10);
        assertEquals(List.of(new CloudOp(DIAMOND, -10)), p.drainTickDebits());
        assertEquals(10, p.pendingCredit(DIAMOND));
    }
}
