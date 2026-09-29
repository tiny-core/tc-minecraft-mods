package org.tinycore.colonybridge.client.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Suaviza os números e as barras de uma tela de monitor: em vez de saltarem quando chega um dado novo
 * (1×/s), eles deslizam até o valor alvo.
 * <p>
 * Existe um animador por tela, guardado pelo {@link MonitorRenderer} enquanto ela estiver sendo
 * desenhada. É só exibição no cliente: nada aqui vai para o servidor nem para o disco.
 * <p>
 * A suavização é <b>por tempo real</b>, não por frame: a cada frame o valor anda uma fração do que
 * falta, calculada a partir do tempo decorrido. Assim a animação tem a mesma duração com 30 ou 200 FPS.
 */
final class MonitorAnimator {

    /** Fração do caminho restante percorrida em 1 segundo (0.92 ≈ chega perto do alvo em ~0,3 s). */
    private static final double SPEED_PER_SECOND = 0.92;
    /** Abaixo disto o valor "cola" no alvo, para não ficar animando frações invisíveis para sempre. */
    private static final double SNAP = 0.01;

    private final List<Double> values = new ArrayList<>();
    private long lastNanos;
    /** Tempo desde o frame anterior, medido uma vez em {@link #beginFrame} e usado por todas as séries. */
    private double frameSeconds;
    /** Momento do último uso, para o renderer descartar telas que saíram de vista. */
    long lastUsedMillis;

    /**
     * Avança a animação e devolve o valor suavizado do índice pedido.
     * Índices diferentes são séries independentes (cada número e cada barra tem o seu).
     */
    float value(int index, double target) {
        while (values.size() <= index) {
            values.add(target); // série nova começa já no alvo (sem animação na primeira vez)
        }
        double next = step(values.get(index), target, frameSeconds);
        values.set(index, next);
        return (float) next;
    }

    /**
     * Um passo da animação: anda de {@code current} em direção a {@code target} a fração correspondente a
     * {@code seconds} de tempo real. Puro (sem relógio) para poder ser testado.
     */
    static double step(double current, double target, double seconds) {
        double next = current + (target - current) * factor(seconds);
        return Math.abs(target - next) < SNAP ? target : next;
    }

    /** Fração do caminho restante a percorrer em {@code seconds}. */
    private static double factor(double seconds) {
        if (seconds <= 0) {
            return 0;
        }
        // 1 - (1 - SPEED)^t : decaimento exponencial, independente da taxa de frames.
        // Teto de 0,5 s: depois de uma pausa (menu aberto, lag) o valor não salta direto ao alvo.
        return 1 - Math.pow(1 - SPEED_PER_SECOND, Math.min(seconds, 0.5));
    }

    /**
     * Chamado uma vez por frame, antes de desenhar a tela. Mede aqui o tempo do frame: medir a cada
     * {@link #value} fazia só a primeira série do frame andar (as outras viam ~0 s decorridos).
     */
    void beginFrame() {
        lastUsedMillis = System.currentTimeMillis();
        long now = System.nanoTime();
        frameSeconds = lastNanos == 0 ? 0 : (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
    }
}
