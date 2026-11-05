package com.pdf2tz.backend.application.pdf.model.structure;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;

import java.util.Objects;

/**
 * Таблица, привязанная к консервативно определённому пути разделов.
 *
 * @param sectionPath путь разделов
 * @param table целостная таблица
 */
public record StructuredTableBlock(
        SectionPath sectionPath,
        ParsedTable table
) implements StructuredDocumentBlock {

    public StructuredTableBlock {
        sectionPath = Objects.requireNonNull(sectionPath, "Table section path must not be null");
        table = Objects.requireNonNull(table, "Structured table must not be null");
    }

    @Override
    public PageRange pageRange() {
        return new PageRange(table.startPageNumber(), table.endPageNumber());
    }
}
