package com.pdf2tz.backend.application.pdf.model.structure;

import java.util.List;
import java.util.Objects;

/**
 * Линеаризованная структурная модель документа перед ограничением по токенам.
 *
 * @param blocks блоки в исходном порядке чтения
 */
public record StructuredDocument(
        List<StructuredDocumentBlock> blocks
) {

    public StructuredDocument {
        blocks = List.copyOf(Objects.requireNonNull(blocks, "Structured document blocks must not be null"));
    }
}
