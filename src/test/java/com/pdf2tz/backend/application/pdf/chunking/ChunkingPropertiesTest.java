package com.pdf2tz.backend.application.pdf.chunking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkingPropertiesTest {

    @Test
    void acceptsConfiguredLimitAndRejectsUnsupportedSmallLimit() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxTokens(512);

        assertEquals(512, properties.maxTokens());
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxTokens(127));
    }
}
