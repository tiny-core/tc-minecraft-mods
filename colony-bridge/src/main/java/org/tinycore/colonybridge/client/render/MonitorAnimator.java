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
        double current = values.get(index);
        double next = current + (target - current) * factor();
        if (Math.abs(target - next) < SNAP) {
            next = target;
        }
        values.set(index, next);
        return (float) next;
    }

    /** Fração a percorrer neste frame, a partir do tempo desde o frame anterior. */
    private double factor() {
        long now = System.nanoTime();
        double seconds = lastNanos == 0 ? 0 : (now - lastNanos) / 1_000_000_000.0;
        lastNanos = now;
        if (seconds <= 0) {
            return 0;
        }
        // 1 - (1 - SPEED)^t : decaimento exponencial, independente da taxa de frames
        return 1 - Math.pow(1 - SPEED_PER_SECOND, Math.min(seconds, 0.5));
    }

    /** Chamado uma vez por frame, antes de desenhar a tela. */
    void beginFrame() {
        lastUsedMillis = System.currentTimeMillis();
    }
}
