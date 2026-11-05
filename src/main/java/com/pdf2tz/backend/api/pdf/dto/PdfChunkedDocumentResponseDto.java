package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;
import java.util.Objects;

/**
 * DTO результата структурного разбиения PDF-документа.
 *
 * @param chunks чанки в порядке чтения
 */
public record PdfChunkedDocumentResponseDto(
        List<PdfDocumentChunkResponseDto> chunks
) {

    public PdfChunkedDocumentResponseDto {
        chunks = List.copyOf(Objects.requireNonNull(chunks, "Chunks must not be null"));
    }
}
