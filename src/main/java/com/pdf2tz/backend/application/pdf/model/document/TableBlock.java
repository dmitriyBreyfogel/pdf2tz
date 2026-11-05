package com.pdf2tz.backend.application.pdf.model.document;

import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;

import java.util.Objects;

/**
 * Табличный блок готового распарсенного документа.
 *
 * <p>Блок содержит уже целостную таблицу. Если таблица физически переносилась
 * между страницами PDF, это отражено внутри {@link ParsedTable#fragments()},
 * а сам блок всё равно остаётся одной логической таблицей документа.</p>
 *
 * @param table целостная таблица документа
 */
public record TableBlock(
        ParsedTable table
) implements DocumentBlock {

    public TableBlock {
        Objects.requireNonNull(table, "Table block table must not be null");
    }
}
