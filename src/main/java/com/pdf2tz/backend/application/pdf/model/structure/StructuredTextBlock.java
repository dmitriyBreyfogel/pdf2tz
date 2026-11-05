package com.pdf2tz.backend.application.pdf.model.structure;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;

import java.util.Objects;

/**
 * Текстовый блок, относящийся к одному пути разделов.
 *
 * @param sectionPath путь разделов
 * @param pageRange диапазон страниц текста
 * @param text текст блока в порядке чтения
 */
public record StructuredTextBlock(
        SectionPath sectionPath,
        PageRange pageRange,
        String text
) implements StructuredDocumentBlock {

    public StructuredTextBlock {
        sectionPath = Objects.requireNonNull(sectionPath, "Text section path must not be null");
        pageRange = Objects.requireNonNull(pageRange, "Text page range must not be null");
        text = Objects.requireNonNull(text, "Structured text must not be null").trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Structured text must not be blank");
        }
    }
}
