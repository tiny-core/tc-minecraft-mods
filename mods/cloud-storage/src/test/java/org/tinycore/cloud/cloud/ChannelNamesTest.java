package org.tinycore.cloud.cloud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ChannelNames}: o texto do jogador vira nome de canal só se for seguro. */
class ChannelNamesTest {

    @Test
    void trimsAndCollapsesSpaces() {
        assertEquals("Minérios raros", ChannelNames.clean("  Minérios   raros "));
    }

    @Test
    void rejectsEmptyTooLongAndControlCharacters() {
        assertNull(ChannelNames.clean("   "));
        assertNull(ChannelNames.clean("x".repeat(ChannelNames.MAX_LENGTH + 1)));
        assertNull(ChannelNames.clean("§cVermelho"));
        assertNull(ChannelNames.clean("a\u0000b"));
        assertEquals("x".repeat(ChannelNames.MAX_LENGTH), ChannelNames.clean("x".repeat(ChannelNames.MAX_LENGTH)));
    }

    @Test
    void duplicatesIgnoreCase() {
        assertTrue(ChannelNames.taken(List.of("Principal", "Minérios"), "MINÉRIOS"));
        assertFalse(ChannelNames.taken(List.of("Principal"), "Comida"));
    }
}
