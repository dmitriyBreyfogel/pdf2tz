package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfDocumentBlockResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfParsedDocumentResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfParsedPageResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTableBlockResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTextBlockResponseDto;
import com.pdf2tz.backend.application.pdf.model.document.DocumentBlock;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Преобразует готовую блочную модель PDF-документа в DTO HTTP-ответа.
 *
 * <p>В отличие от {@link PdfResponseMapper}, этот mapper не сворачивает страницу
 * в одно текстовое поле. Он сохраняет порядок блоков страницы и отдаёт наружу
 * как текстовые, так и табличные блоки.</p>
 */
@Component
public class PdfParsedDocumentResponseMapper {

    private final PdfTableResponseMapper pdfTableResponseMapper;

    public PdfParsedDocumentResponseMapper(PdfTableResponseMapper pdfTableResponseMapper) {
        this.pdfTableResponseMapper = Objects.requireNonNull(
                pdfTableResponseMapper,
                "PDF table response mapper must not be null"
        );
    }

    /**
     * Собирает DTO ответа из готовой блочной модели PDF-документа.
     *
     * @param document внутренняя модель готового документа
     * @return DTO блочного ответа API
     */
    public PdfParsedDocumentResponseDto toResponse(ParsedDocument document) {
        Objects.requireNonNull(document, "Parsed document must not be null");

        AtomicInteger tableNumber = new AtomicInteger(1);
        List<PdfParsedPageResponseDto> pages = document.pages().stream()
                .map(page -> toPageResponse(page, tableNumber))
                .toList();

        return new PdfParsedDocumentResponseDto(pages);
    }

    private PdfParsedPageResponseDto toPageResponse(
            ParsedPage page,
            AtomicInteger tableNumber
    ) {
        return new PdfParsedPageResponseDto(
                page.pageNumber(),
                page.blocks().stream()
                        .map(block -> toBlockResponse(block, tableNumber))
                        .toList()
        );
    }

    private PdfDocumentBlockResponseDto toBlockResponse(
            DocumentBlock block,
            AtomicInteger tableNumber
    ) {
        if (block instanceof TextBlock textBlock) {
            return new PdfTextBlockResponseDto(textBlock.text());
        }

        if (block instanceof TableBlock tableBlock) {
            return new PdfTableBlockResponseDto(
                    pdfTableResponseMapper.toTableResponse(
                            tableNumber.getAndIncrement(),
                            tableBlock.table()
                    )
            );
        }

        throw new IllegalArgumentException("Unsupported PDF document block type: " + block.getClass().getName());
    }
}
