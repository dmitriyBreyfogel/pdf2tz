package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;
import java.util.Objects;

/**
 * DTO одного bounded document chunk.
 *
 * @param chunkNumber номер чанка в рамках запроса
 * @param sectionPath полный путь разделов
 * @param startPageNumber первая исходная страница
 * @param endPageNumber последняя исходная страница
 * @param estimatedTokenCount размер exact content через tokenizer
 * @param content готовый payload для будущего LLM adapter
 */
public record PdfDocumentChunkResponseDto(
        int chunkNumber,
        List<PdfSectionHeadingResponseDto> sectionPath,
        int startPageNumber,
        int endPageNumber,
        int estimatedTokenCount,
        String content
) {

    public PdfDocumentChunkResponseDto {
        sectionPath = List.copyOf(Objects.requireNonNull(sectionPath, "Section path must not be null"));
        content = Objects.requireNonNull(content, "Chunk content must not be null");
    }
}
