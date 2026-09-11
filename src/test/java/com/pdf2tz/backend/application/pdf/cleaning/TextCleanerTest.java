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

    @Test
    void cleanTextFragmentRemovesStandaloneDomainSuffix() {
        String cleanedText = textCleaner.cleanTextFragment(
                ".ru",
                new DocumentNoiseProfile(Set.of(), Set.of())
        );

        assertEquals("", cleanedText);
    }

    @Test
    void cleanTextFragmentKeepsUsefulTextWithDomainSuffixInside() {
        String cleanedText = textCleaner.cleanTextFragment(
                "Сайт производителя example.ru",
                new DocumentNoiseProfile(Set.of(), Set.of())
        );

        assertEquals("Сайт производителя example.ru", cleanedText);
    }

    @Test
    void cleanTextFragmentRemovesStandaloneDomainSuffixTokenInsideText() {
        String cleanedText = textCleaner.cleanTextFragment(
                "6 - индикаторы наличия питания; .ru",
                new DocumentNoiseProfile(Set.of(), Set.of())
        );

        assertEquals("6 - индикаторы наличия питания;", cleanedText);
    }
    @Test
    void cleanTableCellTextRemovesDerivedAsciiWatermarkFragments() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("adzo", "oszd", "ravn", "r.go", "v.ru", "w.r", "ww"),
                Set.of("r.go", "v.ru")
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "No Наименование изделия sz d Децимальный номер rav Количество gov",
                noiseProfile
        );

        assertEquals("No Наименование изделия Децимальный номер Количество", cleanedText);
    }

    @Test
    void cleanTableCellTextKeepsUsefulAsciiTableAbbreviations() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("adzo", "oszd", "ravn"),
                Set.of()
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "No DIN DC VGA USB Type",
                noiseProfile
        );

        assertEquals("No DIN DC VGA USB Type", cleanedText);
    }

    @Test
    void cleanTableCellTextRemovesNumericAsciiWatermarkSuffixes() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("adzo", "r.go", "v.ru"),
                Set.of("r.go", "v.ru")
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "ГАКЕ.080.00.000 -02zo 1 000or or. .g",
                noiseProfile
        );

        assertEquals("ГАКЕ.080.00.000 -02 1 000", cleanedText);
    }

    @Test
    void cleanTableCellTextRemovesStandaloneLineNoiseToken() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("ере"),
                Set.of()
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "Количество, мин-макс ере",
                noiseProfile
        );

        assertEquals("Количество, мин-макс", cleanedText);
    }

    @Test
    void cleanTableCellTextBlanksCellComposedFromLineNoise() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("воох"),
                Set.of()
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "авоохр",
                noiseProfile
        );

        assertEquals("", cleanedText);
    }

    @Test
    void cleanTableCellTextRemovesSpacedAsciiWatermarkFragment() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("r.go", "v.ru"),
                Set.of("r.go", "v.ru")
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "Децимальный номер ra d Количество",
                noiseProfile
        );

        assertEquals("Децимальный номер Количество", cleanedText);
    }

    @Test
    void cleanTableCellTextRemovesRepeatedAsciiLettersInsideCyrillicWords() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("r.go", "v.ru"),
                Set.of("r.go", "v.ru")
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "Децимvалnьнaый номер",
                noiseProfile
        );

        assertEquals("Децимальный номер", cleanedText);
    }

    @Test
    void cleanTableCellTextKeepsSingleAsciiLetterInsideCyrillicWord() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("r.go", "v.ru"),
                Set.of("r.go", "v.ru")
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "Рaзмер",
                noiseProfile
        );

        assertEquals("Рaзмер", cleanedText);
    }

    @Test
    void cleanTableCellTextKeepsUsefulWebsiteAddress() {
        DocumentNoiseProfile noiseProfile = new DocumentNoiseProfile(
                Set.of("ifu 1118.06 ru 10"),
                Set.of()
        );

        String cleanedText = textCleaner.cleanTableCellText(
                "Адрес доступен на www.getinge.com.",
                noiseProfile
        );

        assertEquals("Адрес доступен на www.getinge.com.", cleanedText);
    }
}
