package com.pdf2tz.backend.api.pdf.dto;

import java.util.Objects;

/**
 * DTO текстового блока готового PDF-документа.
 *
 * @param type тип блока
 * @param text очищенный текст блока
 */
public record PdfTextBlockResponseDto(
        PdfDocumentBlockTypeResponseDto type,
        String text
) implements PdfDocumentBlockResponseDto {

    public PdfTextBlockResponseDto(String text) {
        this(PdfDocumentBlockTypeResponseDto.TEXT, text);
    }

    public PdfTextBlockResponseDto {
        if (type != PdfDocumentBlockTypeResponseDto.TEXT) {
            throw new IllegalArgumentException("Text block type must be TEXT");
        }

        text = Objects.requireNonNull(text, "PDF text block text must not be null")
                .trim();
    }
}
