package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO страницы готового распарсенного PDF-документа.
 *
 * @param pageNumber номер страницы исходного PDF-документа
 * @param blocks текст страницы, затем начинающиеся на ней таблицы в порядке сверху вниз
 */
public record PdfParsedPageResponseDto(
        int pageNumber,
        List<PdfDocumentBlockResponseDto> blocks
) { }
