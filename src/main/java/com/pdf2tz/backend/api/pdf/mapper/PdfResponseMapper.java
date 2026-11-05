package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfPageResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Преобразует внутреннюю модель PDF-документа в DTO HTTP-ответа.
 */
@Component
public class PdfResponseMapper {

    /**
     * Собирает DTO ответа из полного очищенного текстового слоя PDF-документа.
     *
     * <p>Текст включает строковое представление таблиц: удаление табличных дублей
     * выполняется только при сборке блочного документа. Пустые страницы сохраняются.</p>
     *
     * @param document очищенный текст документа до сборки блоков
     * @return DTO ответа API
     */
    public PdfResponseDto toResponse(CleanedTextDocument document) {
        Objects.requireNonNull(document, "Parsed document must not be null");

        List<PdfPageResponseDto> pages = document.pages().stream()
                .map(page -> new PdfPageResponseDto(
                        page.pageNumber(),
                        page.text()
                ))
                .toList();

        return new PdfResponseDto(pages);
    }

}
