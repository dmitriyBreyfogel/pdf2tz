package com.pdf2tz.backend.api.pdf.dto;

/**
 * DTO блока готового PDF-документа.
 *
 * <p>Контракт объединяет разные типы блоков страницы: обычный текст и
 * структурированные таблицы. Конкретный состав полей зависит от {@link #type()}.</p>
 */
public sealed interface PdfDocumentBlockResponseDto permits PdfTextBlockResponseDto, PdfTableBlockResponseDto {

    /**
     * Возвращает тип блока страницы.
     *
     * @return тип блока готового документа
     */
    PdfDocumentBlockTypeResponseDto type();
}
