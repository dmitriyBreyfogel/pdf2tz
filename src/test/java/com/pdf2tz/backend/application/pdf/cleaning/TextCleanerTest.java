package com.pdf2tz.backend.application.pdf.cleaning;

import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextCleanerTest {

    private final TextCleaner textCleaner = new TextCleaner();

    @Test
    void cleanTextFragmentRemovesWholeLineNoise() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("watermark"),
                Set.of()
        );

        String cleanedText = textCleaner.cleanTextFragment("  Watermark  ", noiseProfile);

        assertEquals("", cleanedText);
    }

    @Test
    void cleanTextFragmentRemovesInlineNoiseInsideUsefulText() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of(),
                Set.of("example.com")
        );

        String cleanedText = textCleaner.cleanTextFragment(
                "Полезный текст example.com продолжается",
                noiseProfile
        );

        assertEquals("Полезный текст продолжается", cleanedText);
    }
}
