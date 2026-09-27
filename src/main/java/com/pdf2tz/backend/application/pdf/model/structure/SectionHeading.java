package com.pdf2tz.backend.application.pdf.model.structure;

import java.util.Objects;

/**
 * Принятый анализатором заголовок раздела.
 *
 * @param level логический уровень заголовка
 * @param text исходный очищенный текст заголовка
 * @param pageNumber страница, на которой заголовок найден
 */
public record SectionHeading(
        int level,
        String text,
        int pageNumber
) {

    public SectionHeading {
        if (level < 1) {
            throw new IllegalArgumentException("Section heading level must be positive");
        }
        text = Objects.requireNonNull(text, "Section heading text must not be null").trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Section heading text must not be blank");
        }
        if (pageNumber < 1) {
            throw new IllegalArgumentException("Section heading page number must be positive");
        }
    }
}
