package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfContextResponseDto;
import com.pdf2tz.backend.application.pdf.model.retrieval.PreparedPdfContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Переводит подготовленный контекст в HTTP DTO без повторного поиска или подсчёта токенов. */
@Component
public class PdfContextResponseMapper {

    private final PdfChunkedDocumentResponseMapper chunkMapper;

    public PdfContextResponseMapper(PdfChunkedDocumentResponseMapper chunkMapper) {
        this.chunkMapper = Objects.requireNonNull(chunkMapper, "Chunk mapper must not be null");
    }

    /** Сохраняет исходный prompt, готовый контекст и метаданные каждого выбранного чанка. */
    public PdfContextResponseDto toResponse(PreparedPdfContext context) {
        Objects.requireNonNull(context, "Prepared context must not be null");
        return new PdfContextResponseDto(
                context.prompt(),
                context.contextAvailable(),
                context.preparedPrompt(),
                context.estimatedTokenCount(),
                context.totalChunkCount(),
                context.selectedChunks().stream().map(chunkMapper::toChunkResponse).toList()
        );
    }
}
