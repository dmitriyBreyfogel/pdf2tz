package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO готового распарсенного PDF-документа.
 *
 * @param pages страницы в исходном порядке с текстовыми и табличными блоками
 */
public record PdfParsedDocumentResponseDto(
        List<PdfParsedPageResponseDto> pages
) { }
