package com.pdf2tz.backend.api.pdf.dto;

/**
 * Тип блока готового PDF-документа.
 */
public enum PdfDocumentBlockTypeResponseDto {

    /**
     * Блок очищенного текста страницы.
     */
    TEXT,

    /**
     * Блок структурированной таблицы.
     */
    TABLE
}
