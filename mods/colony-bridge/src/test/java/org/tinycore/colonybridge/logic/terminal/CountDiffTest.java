package org.tinycore.colonybridge.logic.terminal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CountDiff}: o que o Terminal do Armazém manda ao cliente. Um erro aqui aparece no jogo como item
 * que continua na grade depois de sair do armazém, ou quantidade que não atualiza.
 */
class CountDiffTest {

    @Test
    void firstSyncSendsEverything() {
        List<CountDiff.Change<String>> changes = CountDiff.between(Map.of(), counts("pão", 64, "carvão", 128));
        assertEquals(List.of(new CountDiff.Change<>("pão", 64L), new CountDiff.Change<>("carvão", 128L)), changes);
    }

    @Test
    void unchangedCountsAreNotSent() {
        Map<String, Long> same = counts("pão", 64, "carvão", 128);
        assertTrue(CountDiff.between(same, counts("pão", 64, "carvão", 128)).isEmpty());
    }

    @Test
    void changedCountIsSentWithTheNewValue() {
        assertEquals(List.of(new CountDiff.Change<>("carvão", 64L)),
                CountDiff.between(counts("pão", 64, "carvão", 128), counts("pão", 64, "carvão", 64)));
    }

    @Test
    void removedItemIsSentAsZero() {
        assertEquals(List.of(new CountDiff.Change<>("carvão", 0L)),
                CountDiff.between(counts("pão", 64, "carvão", 128), counts("pão", 64)));
    }

    /** Mapa com ordem previsível a partir de pares chave, quantidade. */
    private static Map<String, Long> counts(Object... pairs) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], ((Integer) pairs[i + 1]).longValue());
        }
        return map;
    }
}
