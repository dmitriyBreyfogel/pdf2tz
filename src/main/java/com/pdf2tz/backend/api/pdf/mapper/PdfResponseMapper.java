package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfPageResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.document.DocumentBlock;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Преобразует внутреннюю модель PDF-документа в DTO HTTP-ответа.
 */
@Component
public class PdfResponseMapper {

    /**
     * Собирает DTO ответа из очищенного PDF-документа.
     *
     * @param document внутренняя модель очищенного документа
     * @return DTO ответа API
     */
    public PdfResponseDto toResponse(CleanedDocument document) {
        Objects.requireNonNull(document, "Cleaned document must not be null");

        List<PdfPageResponseDto> pages = document.pages().stream()
                .map(page -> new PdfPageResponseDto(
                        page.pageNumber(),
                        page.text()
                ))
                .toList();

        return new PdfResponseDto(pages);
    }

    /**
     * Собирает DTO ответа из готовой блочной модели PDF-документа.
     *
     * <p>Текущий публичный API остаётся text-only, поэтому mapper берёт из
     * страницы только текстовые блоки и объединяет их в одно поле {@code text}.
     * Когда API начнёт отдавать таблицы отдельной структурой, этот mapper нужно
     * будет расширить новым DTO-контрактом.</p>
     *
     * @param document внутренняя модель готового документа
     * @return DTO ответа API
     */
    public PdfResponseDto toResponse(ParsedDocument document) {
        Objects.requireNonNull(document, "Parsed document must not be null");

        List<PdfPageResponseDto> pages = document.pages().stream()
                .map(page -> new PdfPageResponseDto(
                        page.pageNumber(),
                        pageText(page)
                ))
                .toList();

        return new PdfResponseDto(pages);
    }

    private String pageText(ParsedPage page) {
        return page.blocks().stream()
                .map(this::blockText)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n\n"));
    }

    private String blockText(DocumentBlock block) {
        if (block instanceof TextBlock textBlock) {
            return textBlock.text();
        }

        return "";
    }
}
