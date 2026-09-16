package com.pdf2tz.backend.application.pdf.model.document;

import java.util.Objects;

/**
 * Текстовый блок готового распарсенного документа.
 *
 * @param text очищенный текст блока
 */
public record TextBlock(
        String text
) implements DocumentBlock {

    public TextBlock {
        text = Objects.requireNonNull(text, "Text block text must not be null")
                .trim();

        if (text.isBlank()) {
            throw new IllegalArgumentException("Text block text must not be blank");
        }
    }
}
