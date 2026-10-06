package com.pdf2tz.backend.application.pdf.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetrievalPropertiesTest {

    @Test
    void validatesLimitsWithoutChangingChunkingConfiguration() {
        RetrievalProperties properties = new RetrievalProperties();
        assertEquals(8192, properties.maxContextTokens());
        assertEquals(1024, properties.maxPromptTokens());
        assertEquals(6, properties.maxChunks());

        assertThrows(IllegalArgumentException.class, () -> properties.setMaxContextTokens(127));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxPromptTokens(0));
        assertThrows(IllegalArgumentException.class, () -> properties.setMaxChunks(0));
    }
}
