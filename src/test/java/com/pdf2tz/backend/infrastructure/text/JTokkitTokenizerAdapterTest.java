package com.pdf2tz.backend.infrastructure.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JTokkitTokenizerAdapterTest {

    private final JTokkitTokenizerAdapter adapter = new JTokkitTokenizerAdapter();

    @Test
    void countsMixedLanguageText() {
        String text = "Медицинское изделие / medical device: CPAP, ИВЛ, 中文。";

        int totalTokens = adapter.countTokens(text);

        assertTrue(totalTokens > 0);
    }

    @Test
    void validatesArguments() {
        assertThrows(NullPointerException.class, () -> adapter.countTokens(null));
    }
}
