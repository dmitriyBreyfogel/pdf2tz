package com.pdf2tz.backend.infrastructure.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JTokkitTokenizerAdapterTest {

    private final JTokkitTokenizerAdapter adapter = new JTokkitTokenizerAdapter();

    @Test
    void countsMixedLanguageTextAndSplitsWithinLimit() {
        String text = "Медицинское изделие / medical device: CPAP, ИВЛ, 中文。";

        int totalTokens = adapter.countTokens(text);
        List<String> parts = adapter.splitByTokenLimit(text, 3);

        assertTrue(totalTokens > 0);
        assertTrue(parts.stream().allMatch(part -> adapter.countTokens(part) <= 3));
        assertEquals(text, String.join("", parts));
    }

    @Test
    void validatesArguments() {
        assertThrows(NullPointerException.class, () -> adapter.countTokens(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.splitByTokenLimit("text", 0));
        assertEquals(List.of(), adapter.splitByTokenLimit("", 3));
    }
}
