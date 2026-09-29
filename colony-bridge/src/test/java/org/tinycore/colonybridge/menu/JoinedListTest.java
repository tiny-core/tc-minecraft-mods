package org.tinycore.colonybridge.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link JoinedList}: ler e gravar numa lista juntada precisa chegar na lista de origem certa. */
class JoinedListTest {

    @Test
    void getAndSetReachTheRightSourceList() {
        List<String> filter = new ArrayList<>(Arrays.asList("a", "b"));
        List<String> preferred = new ArrayList<>(Arrays.asList("c", "d", "e"));
        JoinedList<String> joined = new JoinedList<>(filter, preferred);

        assertEquals(5, joined.size());
        assertEquals("b", joined.get(1));
        assertEquals("c", joined.get(2)); // primeiro índice da segunda lista

        joined.set(1, "B");
        joined.set(4, "E");
        assertEquals(List.of("a", "B"), filter);
        assertEquals(List.of("c", "d", "E"), preferred);
    }

    @Test
    void outOfRangeFailsInsteadOfWritingSomewhereElse() {
        JoinedList<String> joined = new JoinedList<>(new ArrayList<>(List.of("a")), new ArrayList<>(List.of("b")));
        assertThrows(IndexOutOfBoundsException.class, () -> joined.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> joined.set(5, "x"));
    }
}
