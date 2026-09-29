package org.tinycore.colonybridge.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MonitorShape}: quando um grupo de monitores vira uma tela única. Coordenadas: {@code u} para a
 * direita de quem olha, {@code v} para cima.
 */
class MonitorShapeTest {

    private static MonitorShape rectangle(int u0, int v0, int width, int height) {
        MonitorShape shape = new MonitorShape();
        for (int u = u0; u < u0 + width; u++) {
            for (int v = v0; v < v0 + height; v++) {
                shape.add(u, v);
            }
        }
        return shape;
    }

    @Test
    void fullRectangleIsValid() {
        MonitorShape shape = rectangle(0, 0, 3, 2);
        assertTrue(shape.isValid(8, 6));
        assertEquals(3, shape.width());
        assertEquals(2, shape.height());
    }

    @Test
    void singleBlockIsValid() {
        assertTrue(rectangle(0, 0, 1, 1).isValid(8, 6));
    }

    @Test
    void holeOrLShapeIsInvalid() {
        MonitorShape l = new MonitorShape();
        l.add(0, 0);
        l.add(1, 0);
        l.add(0, 1); // falta (1,1): formato L
        assertFalse(l.isValid(8, 6));
    }

    @Test
    void tooWideOrTooTallIsInvalid() {
        assertFalse(rectangle(0, 0, 9, 1).isValid(8, 6));
        assertFalse(rectangle(0, 0, 1, 7).isValid(8, 6));
        assertTrue(rectangle(0, 0, 8, 6).isValid(8, 6), "exatamente no limite vale");
    }

    @Test
    void masterCornerIsTheLowestLeft() {
        // O bloco de partida pode estar no meio: as coordenadas são relativas a ele e podem ser negativas.
        MonitorShape shape = rectangle(-2, -1, 4, 3);
        assertEquals(-2, shape.minU());
        assertEquals(-1, shape.minV());
    }

    @Test
    void emptyIsInvalid() {
        assertFalse(new MonitorShape().isValid(8, 6));
    }
}
