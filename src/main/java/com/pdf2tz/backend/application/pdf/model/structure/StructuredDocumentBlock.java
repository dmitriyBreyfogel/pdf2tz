package com.pdf2tz.backend.application.pdf.model.structure;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;

/**
 * Структурированный блок документа с восстановленным контекстом раздела.
 */
public sealed interface StructuredDocumentBlock
        permits StructuredHeadingBlock, StructuredTextBlock, StructuredTableBlock {

    SectionPath sectionPath();

    PageRange pageRange();
}
