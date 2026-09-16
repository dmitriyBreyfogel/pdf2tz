package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO готового распарсенного PDF-документа.
 *
 * @param pages страницы документа с блоками в порядке чтения
 */
public record PdfParsedDocumentResponseDto(
        List<PdfParsedPageResponseDto> pages
) { }
