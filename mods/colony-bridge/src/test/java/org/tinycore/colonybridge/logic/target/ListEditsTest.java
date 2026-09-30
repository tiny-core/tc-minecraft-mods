package org.tinycore.colonybridge.logic.target;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ListEdits}: índices e tamanhos vêm de pacotes do cliente. Índice inválido ou lista cheia não pode
 * lançar exceção (derrubaria o servidor) nem mudar nada.
 */
class ListEditsTest {

    private static List<String> list(String... items) {
        return new ArrayList<>(List.of(items));
    }

    @Test
    void addRespectsMaxLines() {
        List<String> lines = list("a", "b");
        assertTrue(ListEdits.add(lines, "c", 3));
        assertFalse(ListEdits.add(lines, "d", 3));
        assertEquals(List.of("a", "b", "c"), lines);
    }

    @Test
    void setAndRemoveIgnoreInvalidIndex() {
        List<String> lines = list("a", "b");
        assertFalse(ListEdits.set(lines, -1, "x"));
        assertFalse(ListEdits.set(lines, 2, "x"));
        assertFalse(ListEdits.remove(lines, 5));
        assertEquals(List.of("a", "b"), lines);
    }

    @Test
    void removeShiftsLinesUp() {
        List<String> lines = list("a", "b", "c");
        assertTrue(ListEdits.remove(lines, 0));
        assertTrue(ListEdits.set(lines, 1, "z"));
        assertEquals(List.of("b", "z"), lines);
    }

    @Test
    void trimKeepsFirstLines() {
        List<String> lines = list("a", "b", "c", "d");
        assertTrue(ListEdits.trim(lines, 2));
        assertEquals(List.of("a", "b"), lines);
        assertFalse(ListEdits.trim(lines, 5));
    }

    @Test
    void trimToZeroOrNegativeEmptiesList() {
        List<String> lines = list("a");
        assertTrue(ListEdits.trim(lines, -1));
        assertTrue(lines.isEmpty());
    }
}
