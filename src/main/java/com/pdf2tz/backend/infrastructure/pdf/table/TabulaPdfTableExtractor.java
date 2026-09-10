package com.pdf2tz.backend.infrastructure.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import com.pdf2tz.backend.application.ports.PdfTableExtractorPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Извлекает сырые табличные кандидаты из PDF через Tabula.
 *
 * <p>Адаптер относится к infrastructure-слою: он знает о PDFBox и Tabula,
 * но наружу возвращает только внутреннюю application-модель
 * {@link TableCandidate}. Дальнейший выбор кандидатов, очистка ячеек,
 * нормализация строк и склейка многостраничных таблиц выполняются в
 * application-сервисах.</p>
 */
@Component
public class TabulaPdfTableExtractor implements PdfTableExtractorPort {

    private final SpreadsheetExtractionAlgorithm spreadsheetExtractionAlgorithm;
    private final BasicExtractionAlgorithm basicExtractionAlgorithm;

    public TabulaPdfTableExtractor() {
        this.spreadsheetExtractionAlgorithm = new SpreadsheetExtractionAlgorithm();
        this.basicExtractionAlgorithm = new BasicExtractionAlgorithm();
    }

    /**
     * Извлекает табличные кандидаты со всех страниц PDF-документа.
     *
     * <p>Сначала используется spreadsheet/lattice-режим Tabula, который лучше
     * работает с таблицами, у которых есть видимые линии. Если на странице он
     * ничего не нашёл, применяется basic/stream-режим, который ориентируется
     * на расположение текста.</p>
     *
     * @param content байтовое представление PDF-файла
     * @return сырые табличные кандидаты
     */
    @Override
    public List<TableCandidate> extract(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");

        try (
                PDDocument document = Loader.loadPDF(content);
                ObjectExtractor objectExtractor = new ObjectExtractor(document)
        ) {
            return extractCandidates(objectExtractor);
        }
        catch (IOException | RuntimeException e) {
            throw AppException.build(
                    ErrorCode.PDF_PARSE_ERROR,
                    "Не удалось извлечь таблицы из PDF файла",
                    Map.of("detail", e.getMessage())
            );
        }
    }

    private List<TableCandidate> extractCandidates(ObjectExtractor objectExtractor) {
        List<TableCandidate> candidates = new ArrayList<>();
        PageIterator pages = objectExtractor.extract();

        while (pages.hasNext()) {
            Page page = pages.next();

            candidates.addAll(extractPageCandidates(page));
        }

        return List.copyOf(candidates);
    }

    private List<TableCandidate> extractPageCandidates(Page page) {
        List<Table> tables = spreadsheetExtractionAlgorithm.extract(page);

        if (tables.isEmpty()) {
            tables = basicExtractionAlgorithm.extract(page);
        }

        return tables.stream()
                .filter(this::hasRows)
                .map(table -> toCandidate(table, page))
                .toList();
    }

    private boolean hasRows(Table table) {
        return table.getRowCount() > 0 && table.getColCount() > 0;
    }

    private TableCandidate toCandidate(
            Table table,
            Page page
    ) {
        return new TableCandidate(
                toArea(table, page),
                toRows(table)
        );
    }

    private TableArea toArea(
            Table table,
            Page page
    ) {
        int pageNumber = table.getPageNumber() > 0
                ? table.getPageNumber()
                : page.getPageNumber();

        return new TableArea(
                pageNumber,
                table.getTop(),
                table.getLeft(),
                table.getBottom(),
                table.getRight()
        );
    }

    private List<TableRow> toRows(Table table) {
        return table.getRows().stream()
                .map(this::toRow)
                .toList();
    }

    private TableRow toRow(List<RectangularTextContainer> cells) {
        return new TableRow(cells.stream()
                .map(this::toCell)
                .toList());
    }

    private TableCell toCell(RectangularTextContainer cell) {
        return new TableCell(cell.getText());
    }
}
