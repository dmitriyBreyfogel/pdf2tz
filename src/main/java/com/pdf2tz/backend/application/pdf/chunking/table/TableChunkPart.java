package com.pdf2tz.backend.application.pdf.chunking.table;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;

import java.util.Objects;

/**
 * Сериализованная часть таблицы и страницы строк, содержащихся в этой части.
 * Повторная шапка в следующих частях служит контекстом и не расширяет диапазон.
 *
 * @param content текст части таблицы
 * @param sourcePages диапазон страниц её исходных строк
 */
public record TableChunkPart(String content, PageRange sourcePages) {
    public TableChunkPart {
        content = Objects.requireNonNull(content, "Table chunk content must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("Table chunk content must not be blank");
        }
        sourcePages = Objects.requireNonNull(sourcePages, "Table chunk source pages must not be null");
    }
}
