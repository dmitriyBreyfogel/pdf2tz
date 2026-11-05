package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfChunkedDocumentResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfDocumentChunkResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfSectionHeadingResponseDto;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Преобразует application-модель chunks в HTTP DTO.
 *
 * <p>Mapper не пересчитывает token count и не содержит эвристик определения
 * заголовков: вся семантика уже сформирована application-слоем.</p>
 */
@Component
public class PdfChunkedDocumentResponseMapper {

    /**
     * Собирает DTO ответа chunks.
     *
     * @param document chunked document
     * @return API response DTO
     */
    public PdfChunkedDocumentResponseDto toResponse(ChunkedDocument document) {
        Objects.requireNonNull(document, "Chunked document must not be null");
        return new PdfChunkedDocumentResponseDto(
                document.chunks().stream()
                        .map(this::toChunkResponse)
                        .toList()
        );
    }

    /** Преобразует один чанк, сохраняя его страницы и полный путь раздела. */
    public PdfDocumentChunkResponseDto toChunkResponse(DocumentChunk chunk) {
        return new PdfDocumentChunkResponseDto(
                chunk.chunkNumber(),
                chunk.sectionPath().headings().stream()
                        .map(heading -> new PdfSectionHeadingResponseDto(
                                heading.level(),
                                heading.text(),
                                heading.pageNumber()
                        ))
                        .toList(),
                chunk.pageRange().startPageNumber(),
                chunk.pageRange().endPageNumber(),
                chunk.estimatedTokenCount(),
                chunk.content()
        );
    }
}
