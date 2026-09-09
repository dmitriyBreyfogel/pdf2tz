package com.pdf2tz.backend.application.pdf.cleaning;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentNoiseProfileBuilderTest {

    private final DocumentNoiseProfileBuilder profileBuilder = new DocumentNoiseProfileBuilder();
    private final TextCleaner textCleaner = new TextCleaner();

    @Test
    void detectsFragmentedWatermarkAsLineNoiseOnly() {
        ExtractedDocument document = documentWithRepeatedText("""
                пол
                учен
                офиц
                сай
                фед
                ерал
                надз
                здра
                r.go
                v.ru
                Полезный текст страницы %1$d.
                Дозировка по инструкции сохраняется.
                Дополнительная строка страницы %1$d.
                Описание продолжается на странице %1$d.
                Нижняя строка страницы %1$d.
                """);

        DocumentNoiseProfile noiseProfile = profileBuilder.build(document);
        CleanedDocument cleanedDocument = textCleaner.cleanDocument(document, noiseProfile);
        String cleanedText = joinPages(cleanedDocument);

        assertTrue(noiseProfile.lineNoise().contains("пол"));
        assertTrue(noiseProfile.lineNoise().contains("v.ru"));
        assertFalse(noiseProfile.inlineNoise().contains("v.ru"));
        assertFalse(cleanedText.contains("\nпол\n"));
        assertFalse(cleanedText.contains("\nv.ru\n"));
        assertTrue(cleanedText.contains("Дозировка по инструкции сохраняется."));
    }

    @Test
    void keepsSingleRepeatedShortUsefulLine() {
        ExtractedDocument document = documentWithRepeatedText("""
                Да
                Полезное описание страницы %d.
                """);

        DocumentNoiseProfile noiseProfile = profileBuilder.build(document);
        CleanedDocument cleanedDocument = textCleaner.cleanDocument(document, noiseProfile);
        String cleanedText = joinPages(cleanedDocument);

        assertFalse(noiseProfile.lineNoise().contains("да"));
        assertTrue(cleanedText.contains("Да"));
    }

    private ExtractedDocument documentWithRepeatedText(String pageTemplate) {
        List<ExtractedPage> pages = IntStream.rangeClosed(1, 10)
                .mapToObj(pageNumber -> new ExtractedPage(
                        pageNumber,
                        pageTemplate.formatted(pageNumber)
                ))
                .toList();

        return new ExtractedDocument(pages);
    }

    private String joinPages(CleanedDocument document) {
        return document.pages().stream()
                .map(page -> "\n" + page.text() + "\n")
                .reduce("", String::concat);
    }
}
