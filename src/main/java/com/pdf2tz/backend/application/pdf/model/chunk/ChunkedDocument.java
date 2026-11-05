package com.pdf2tz.backend.application.pdf.model.chunk;

import java.util.List;
import java.util.Objects;

/**
 * Результат структурного разбиения документа на упорядоченные чанки.
 *
 * @param chunks чанки в порядке чтения
 */
public record ChunkedDocument(
        List<DocumentChunk> chunks
) {

    public ChunkedDocument {
        chunks = List.copyOf(Objects.requireNonNull(chunks, "Chunked document chunks must not be null"));
    }
}
