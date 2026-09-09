package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfPageResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Преобразует внутреннюю модель PDF-документа в DTO HTTP-ответа.
 */
@Component
public class PdfResponseMapper {

    /**
     * Собирает DTO ответа из извлечённого PDF-документа.
     *
     * @param document внутренняя модель извлечённого документа
     * @return DTO ответа API
     */
    public PdfResponseDto toResponse(ExtractedDocument document) {
        List<PdfPageResponseDto> pages = document.pages().stream()
                .map(page -> new PdfPageResponseDto(
                        page.pageNumber(),
                        page.text()
                ))
                .toList();

        return new PdfResponseDto(pages);
    }

    /**
     * Собирает DTO ответа из очищенного PDF-документа.
     *
     * @param document внутренняя модель очищенного документа
     * @return DTO ответа API
     */
    public PdfResponseDto toResponse(CleanedDocument document) {
        List<PdfPageResponseDto> pages = document.pages().stream()
                .map(page -> new PdfPageResponseDto(
                        page.pageNumber(),
                        page.text()
                ))
                .toList();

        return new PdfResponseDto(pages);
    }
}
