package org.tinycore.colonybridge.logic.loader;

import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ChunkSelection}: com a colônia maior que o máximo, ficam os chunks mais perto do centro (onde estão
 * os prédios principais), sempre na mesma ordem.
 */
class ChunkSelectionTest {

    @Test
    void packRoundTripsNegativeCoordinates() {
        long chunk = ChunkSelection.pack(-3, 7);
        assertEquals(-3, ChunkSelection.x(chunk));
        assertEquals(7, ChunkSelection.z(chunk));
    }

    @Test
    void keepsTheNearestToTheCenter() {
        List<Long> claimed = List.of(ChunkSelection.pack(5, 5), ChunkSelection.pack(0, 0), ChunkSelection.pack(1, 0),
                ChunkSelection.pack(-4, 0));
        LongList chosen = ChunkSelection.nearest(claimed, 0, 0, 2);
        assertEquals(List.of(ChunkSelection.pack(0, 0), ChunkSelection.pack(1, 0)), chosen);
    }

    @Test
    void everythingWhenUnderTheLimit() {
        List<Long> claimed = List.of(ChunkSelection.pack(1, 1), ChunkSelection.pack(2, 2));
        assertEquals(2, ChunkSelection.nearest(claimed, 0, 0, 10).size());
    }

    @Test
    void tiesAreStable() {
        List<Long> claimed = List.of(ChunkSelection.pack(0, 1), ChunkSelection.pack(1, 0), ChunkSelection.pack(0, -1));
        LongList first = ChunkSelection.nearest(claimed, 0, 0, 3);
        LongList second = ChunkSelection.nearest(List.of(claimed.get(2), claimed.get(0), claimed.get(1)), 0, 0, 3);
        assertEquals(first, second);
    }
}
