package org.tinycore.colonybridge.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MonitorAnimator}: números e barras do monitor deslizam até o valor novo, na mesma velocidade
 * com qualquer FPS.
 */
class MonitorAnimatorTest {

    @Test
    void noTimeMeansNoMovement() {
        assertEquals(10.0, MonitorAnimator.step(10, 100, 0));
    }

    @Test
    void movesTowardsTargetWithoutOvershooting() {
        double next = MonitorAnimator.step(0, 100, 0.1);
        assertTrue(next > 0 && next < 100, "anda parte do caminho: " + next);
        double down = MonitorAnimator.step(100, 0, 0.1);
        assertTrue(down > 0 && down < 100, "descendo também: " + down);
    }

    @Test
    void sameDurationRegardlessOfFrameRate() {
        // 1 s em 30 frames ou em 200 frames chega ao mesmo ponto.
        double slow = 0;
        for (int i = 0; i < 30; i++) {
            slow = MonitorAnimator.step(slow, 1000, 1.0 / 30);
        }
        double fast = 0;
        for (int i = 0; i < 200; i++) {
            fast = MonitorAnimator.step(fast, 1000, 1.0 / 200);
        }
        assertEquals(slow, fast, 1.0);
    }

    @Test
    void longPauseDoesNotJumpStraightToTarget() {
        // Depois de 10 s parado (menu aberto), o passo é limitado a 0,5 s.
        assertEquals(MonitorAnimator.step(0, 1000, 0.5), MonitorAnimator.step(0, 1000, 10));
    }

    @Test
    void snapsWhenVeryClose() {
        assertEquals(100.0, MonitorAnimator.step(99.995, 100, 0.016));
    }
}
