package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO страницы готового распарсенного PDF-документа.
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param blocks блоки страницы в порядке чтения
 */
public record PdfParsedPageResponseDto(
        int pageNumber,
        List<PdfDocumentBlockResponseDto> blocks
) { }
