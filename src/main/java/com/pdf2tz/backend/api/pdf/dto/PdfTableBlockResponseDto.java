package com.pdf2tz.backend.api.pdf.dto;

import java.util.Objects;

/**
 * DTO табличного блока готового PDF-документа.
 *
 * @param type тип блока
 * @param table структурированная таблица документа
 */
public record PdfTableBlockResponseDto(
        PdfDocumentBlockTypeResponseDto type,
        PdfTableResponseDto table
) implements PdfDocumentBlockResponseDto {

    public PdfTableBlockResponseDto(PdfTableResponseDto table) {
        this(PdfDocumentBlockTypeResponseDto.TABLE, table);
    }

    public PdfTableBlockResponseDto {
        if (type != PdfDocumentBlockTypeResponseDto.TABLE) {
            throw new IllegalArgumentException("Table block type must be TABLE");
        }

        Objects.requireNonNull(table, "PDF table block table must not be null");
    }
}
