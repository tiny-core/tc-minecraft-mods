package org.tinycore.cloud.cloud;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simulação de crash: milhares de partidas aleatórias de depósitos e retiradas com o mundo sendo gravado
 * como o Minecraft grava (chunks a qualquer momento, IO assíncrono, autosave). Em CADA passo verifica o que
 * sobraria em disco se o servidor morresse ali: itens no disco do mundo + saldo durável da nuvem nunca
 * podem passar do total real (isso seria duplicação). Numa parada limpa, a soma tem de bater exatamente.
 *
 * <p>Modelo do mundo (resultado da fase 0, plano §11):
 * <ul>
 *   <li>o mundo é um chunk com uma quantidade do item, em memória e em disco;</li>
 *   <li>gravar um chunk põe uma cópia numa fila de IO; a fila grava no disco depois, em ordem;</li>
 *   <li>a fila nunca termina dentro do mesmo tick em que o chunk entrou nela (premissa aceita: a gravação é
 *       assíncrona e o diário é síncrono no fim do tick);</li>
 *   <li>o IO de um autosave termina antes do autosave seguinte (minutos depois).</li>
 * </ul>
 * Não modela itens mudando de chunk: isso já pode duplicar no vanilla puro após um crash, sem a nuvem.
 */
class CrashSimulationTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID CHANNEL = UUID.randomUUID();
    private static final BalanceKey KEY = new BalanceKey(CHANNEL, "diamond");

    private static final int GAMES = 3000;
    private static final int TICKS = 300;
    private static final int AUTOSAVE_EVERY = 25;

    @Test
    void nenhumCrashDuplica() {
        for (int seed = 0; seed < GAMES; seed++) {
            new Game(new Random(seed)).play(seed);
        }
    }

    private static final class Game {
        final Random rnd;
        final long total;
        long worldMem;
        long worldDisk;
        final long initialCloud;
        long durableDelta;
        /** Cópias do chunk esperando gravação: {quantidade, tick em que entrou na fila}. */
        final Deque<long[]> ioQueue = new ArrayDeque<>();
        final PlayerCloudSession session;

        Game(Random rnd) {
            this.rnd = rnd;
            this.worldMem = rnd.nextInt(500);
            this.worldDisk = worldMem;
            this.initialCloud = rnd.nextInt(500);
            this.total = worldMem + initialCloud;
            this.session = new PlayerCloudSession(PLAYER, 1, 0, Set.of(CHANNEL),
                    initialCloud > 0 ? Map.of(KEY, initialCloud) : Map.of(), CloudQuota.UNLIMITED);
        }

        void play(int seed) {
            for (int tick = 1; tick <= TICKS; tick++) {
                int actions = rnd.nextInt(4);
                for (int i = 0; i < actions; i++) {
                    switch (rnd.nextInt(3)) {
                        case 0 -> deposit();
                        case 1 -> extract();
                        default -> queueChunk(tick); // gravação "ansiosa" do vanilla, no meio do tick
                    }
                    check(seed, tick);
                }
                journal(session.endOfTick());
                check(seed, tick);
                completeIo(tick, rnd.nextBoolean());
                check(seed, tick);
                if (tick % AUTOSAVE_EVERY == 0) {
                    completeIo(Integer.MAX_VALUE, true); // o IO do save anterior já terminou
                    check(seed, tick);
                    queueChunk(tick);                     // o save enfileira os chunks...
                    journal(session.onWorldSave());       // ...e só então vem o LevelEvent.Save
                    check(seed, tick);
                }
            }
            // Parada limpa: save com flush (espera o IO) e só depois o mod grava os créditos.
            queueChunk(TICKS + 1);
            completeIo(Integer.MAX_VALUE, true);
            journal(session.onFlushedSave());
            assertEquals(total, worldDisk + initialCloud + durableDelta,
                    "parada limpa tem de fechar exato (seed " + seed + ")");
            assertTrue(session.isSettled());
        }

        void deposit() {
            long amount = worldMem == 0 ? 0 : 1 + rnd.nextInt((int) Math.min(worldMem, 64));
            if (amount == 0) return;
            long accepted = session.insert(KEY, amount, false);
            worldMem -= accepted;
        }

        void extract() {
            long have = session.available(KEY);
            if (have == 0) return;
            long taken = session.extract(KEY, 1 + rnd.nextInt((int) Math.min(have, 64)), false);
            worldMem += taken;
        }

        void queueChunk(int tick) {
            ioQueue.addLast(new long[]{worldMem, tick});
        }

        /** Grava no disco o prefixo da fila que entrou ANTES de {@code tickLimit}. */
        void completeIo(int tickLimit, boolean all) {
            while (!ioQueue.isEmpty() && ioQueue.peekFirst()[1] < tickLimit) {
                worldDisk = ioQueue.pollFirst()[0];
                if (!all && rnd.nextBoolean()) return;
            }
        }

        void journal(List<Batch> batches) {
            for (Batch b : batches) b.ops().forEach(op -> durableDelta += op.delta());
        }

        void check(int seed, int tick) {
            long afterCrash = worldDisk + initialCloud + durableDelta;
            assertTrue(afterCrash <= total,
                    "duplicação após crash: " + afterCrash + " > " + total + " (seed " + seed + ", tick " + tick + ")");
            assertEquals(total, worldMem + session.available(KEY), "em memória a soma é sempre exata");
        }
    }
}
