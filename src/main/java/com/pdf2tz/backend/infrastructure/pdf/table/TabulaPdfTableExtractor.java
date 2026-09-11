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
import java.util.Set;

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

    /**
     * Максимальная доля страницы, которую обычный stream-кандидат может занимать без дополнительной проверки.
     *
     * <p>Basic/stream-режим Tabula иногда принимает весь текстовый слой
     * страницы за одну большую таблицу, особенно на документах с водяными
     * знаками. Такой кандидат покрывает почти всю страницу и содержит обычный
     * текст, разбитый на случайные колонки. Такие результаты не передаются
     * наружу целиком: адаптер пытается восстановить из них только компактные
     * табличные группы строк.</p>
     */
    private static final double FULL_PAGE_STREAM_TABLE_AREA_RATIO = 0.85;

    /**
     * Допуск для проверки, что stream-кандидат начинается или заканчивается
     * прямо на границе страницы.
     */
    private static final double PAGE_EDGE_TOLERANCE_POINTS = 2.0;

    /**
     * Максимальная высота строки, которую можно считать частью таблицы при восстановлении full-page stream-результата.
     *
     * <p>Когда Tabula ошибочно отдаёт всю страницу, обычные абзацы часто попадают в одну строку большой высоты,
     * потому что в них смешиваются несколько визуальных строк и фрагменты watermark. Табличные строки обычно
     * компактнее, поэтому высокий ряд считается текстовым абзацем и разрывает текущую группу.</p>
     */
    private static final double MAX_RECOVERABLE_ROW_HEIGHT_POINTS = 45.0;

    /**
     * Максимальный вертикальный разрыв между соседними строками одной восстановленной табличной группы.
     */
    private static final double MAX_RECOVERABLE_ROW_GAP_POINTS = 38.0;

    /**
     * Минимальное количество строк во фрагменте, восстановленном из full-page stream-результата.
     *
     * <p>Правило защищает от случайных двухстрочных блоков, подписей и колонтитулов, которые геометрически похожи
     * на таблицу, но не являются полезной табличной областью.</p>
     */
    private static final int MIN_RECOVERABLE_GROUP_ROW_COUNT = 3;

    /**
     * Минимальная ширина восстановленной табличной группы в PDF points.
     */
    private static final double MIN_RECOVERABLE_GROUP_WIDTH_POINTS = 120.0;

    /**
     * Правая часть страницы, где короткие фрагменты чаще всего являются обрывками диагонального watermark.
     */
    private static final double WATERMARK_OUTLIER_LEFT_RATIO = 0.72;

    /**
     * Максимальная длина короткого правого фрагмента, который можно безопасно считать обрывком watermark.
     */
    private static final int MAX_WATERMARK_OUTLIER_TEXT_LENGTH = 3;

    /**
     * Слова, которые часто встречаются в заголовках реальных таблиц.
     *
     * <p>Этот список используется только как защитная эвристика при восстановлении full-page stream-результата.
     * Для обычных таблиц с линиями и нормальных stream-кандидатов он не применяется.</p>
     */
    private static final Set<String> TABLE_HEADER_TERMS = Set.of(
            "наименование",
            "значение",
            "количество",
            "параметр",
            "символ",
            "расшифровка",
            "изделия",
            "таблица",
            "name",
            "value",
            "quantity",
            "parameter",
            "symbol",
            "description",
            "table"
    );

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
     * на расположение текста. Stream-кандидаты дополнительно фильтруются от
     * ложного результата вида "вся страница как таблица".</p>
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
        List<Table> tables = extractSpreadsheetTables(page);

        if (!tables.isEmpty()) {
            return tables.stream()
                    .filter(this::hasRows)
                    .map(table -> toCandidate(table, page))
                    .toList();
        }

        return basicExtractionAlgorithm.extract(page).stream()
                .filter(this::hasRows)
                .flatMap(table -> toStreamCandidates(table, page).stream())
                .toList();
    }

    /**
     * Выполняет lattice-извлечение таблиц и мягко откатывается к stream-режиму при неподдержанной геометрии.
     *
     * <p>На некоторых PDF Tabula встречает наклонные или нестрого ортогональные линии и выбрасывает
     * {@link IllegalArgumentException}. Для нашей задачи это не ошибка всего документа: таблицы с линиями
     * на такой странице могут быть пропущены, но basic/stream-режим всё ещё может извлечь полезный кандидат.</p>
     */
    private List<Table> extractSpreadsheetTables(Page page) {
        try {
            return spreadsheetExtractionAlgorithm.extract(page);
        }
        catch (IllegalArgumentException e) {
            if (isUnsupportedSpreadsheetGeometry(e)) {
                return List.of();
            }

            throw e;
        }
    }

    private boolean isUnsupportedSpreadsheetGeometry(IllegalArgumentException e) {
        return e.getMessage() != null
                && e.getMessage().contains("lines must be orthogonal");
    }

    private boolean hasRows(Table table) {
        return table.getRowCount() > 0 && table.getColCount() > 0;
    }

    private List<TableCandidate> toStreamCandidates(
            Table table,
            Page page
    ) {
        if (isAlmostFullPageStreamTable(table, page)) {
            return recoverCandidatesFromFullPageStreamTable(table, page);
        }

        return List.of(toCandidate(table, page));
    }

    private boolean isAlmostFullPageStreamTable(
            Table table,
            Page page
    ) {
        double pageArea = page.getWidth() * page.getHeight();

        if (pageArea <= 0) {
            return false;
        }

        double tableAreaRatio = table.getArea() / pageArea;

        return tableAreaRatio >= FULL_PAGE_STREAM_TABLE_AREA_RATIO
                && startsAtPageTopLeft(table)
                && coversPageBottomRight(table, page);
    }

    private boolean startsAtPageTopLeft(Table table) {
        return table.getTop() <= PAGE_EDGE_TOLERANCE_POINTS
                && table.getLeft() <= PAGE_EDGE_TOLERANCE_POINTS;
    }

    private boolean coversPageBottomRight(
            Table table,
            Page page
    ) {
        return table.getBottom() >= pageBottom(page) - PAGE_EDGE_TOLERANCE_POINTS
                && table.getRight() >= pageRight(page) - PAGE_EDGE_TOLERANCE_POINTS;
    }

    /**
     * Восстанавливает компактные табличные области из stream-результата, который Tabula ошибочно растянула
     * на всю страницу.
     *
     * <p>Алгоритм намеренно консервативен: он не пытается распознать любую визуально похожую таблицу, а берёт
     * только группы компактных строк с признаками табличного заголовка. Если таких признаков нет, весь full-page
     * результат отбрасывается как обычный текст страницы.</p>
     */
    private List<TableCandidate> recoverCandidatesFromFullPageStreamTable(
            Table table,
            Page page
    ) {
        List<RowSnapshot> rows = table.getRows().stream()
                .map(RowSnapshot::new)
                .filter(row -> !row.isBlank())
                .toList();

        List<List<RowSnapshot>> groups = recoverRowGroups(rows);

        return groups.stream()
                .filter(group -> isValidRecoveredGroup(group, page))
                .map(group -> toRecoveredCandidate(group, table, page))
                .toList();
    }

    private List<List<RowSnapshot>> recoverRowGroups(List<RowSnapshot> rows) {
        List<List<RowSnapshot>> groups = new ArrayList<>();
        List<RowSnapshot> currentGroup = new ArrayList<>();

        for (RowSnapshot row : rows) {
            if (!isRecoverableRow(row)) {
                addCurrentGroup(groups, currentGroup);
                currentGroup = new ArrayList<>();
                continue;
            }

            if (!currentGroup.isEmpty()
                    && isStandaloneTableCaption(row)
                    && hasTableHeader(currentGroup)) {
                addCurrentGroup(groups, currentGroup);
                currentGroup = new ArrayList<>();
            }

            if (!currentGroup.isEmpty() && verticalGap(currentGroup.get(currentGroup.size() - 1), row)
                    > MAX_RECOVERABLE_ROW_GAP_POINTS) {
                addCurrentGroup(groups, currentGroup);
                currentGroup = new ArrayList<>();
            }

            currentGroup.add(row);
        }

        addCurrentGroup(groups, currentGroup);

        return groups;
    }

    private void addCurrentGroup(
            List<List<RowSnapshot>> groups,
            List<RowSnapshot> currentGroup
    ) {
        if (!currentGroup.isEmpty()) {
            groups.add(List.copyOf(currentGroup));
        }
    }

    private boolean isRecoverableRow(RowSnapshot row) {
        if (row.height() > MAX_RECOVERABLE_ROW_HEIGHT_POINTS) {
            return false;
        }

        if (isNonTableSectionHeading(row)) {
            return false;
        }

        if (row.filledCellCount() >= 2) {
            return true;
        }

        return row.textLength() <= 240;
    }

    private boolean isValidRecoveredGroup(
            List<RowSnapshot> group,
            Page page
    ) {
        if (group.size() < MIN_RECOVERABLE_GROUP_ROW_COUNT) {
            return false;
        }

        double width = recoveredGroupRight(group, page) - recoveredGroupLeft(group, page);

        return width >= MIN_RECOVERABLE_GROUP_WIDTH_POINTS
                && hasTableHeader(group);
    }

    private boolean hasTableHeader(List<RowSnapshot> group) {
        return group.stream().anyMatch(this::isLikelyTableHeader);
    }

    private boolean isLikelyTableHeader(RowSnapshot row) {
        String text = row.normalizedText();
        long matchedTermCount = TABLE_HEADER_TERMS.stream()
                .filter(text::contains)
                .count();

        return text.contains("таблица")
                || text.contains("table")
                || matchedTermCount >= 2
                || (matchedTermCount >= 1 && (text.contains("№") || text.contains("no ")));
    }

    private boolean isStandaloneTableCaption(RowSnapshot row) {
        String text = row.normalizedText();

        return text.matches(".*\\bтаблица\\s+\\d+.*")
                || text.matches(".*\\btable\\s+\\d+.*");
    }

    private boolean isNonTableSectionHeading(RowSnapshot row) {
        String text = row.normalizedText();

        return !isLikelyTableHeader(row)
                && (text.matches("\\d+\\.\\s+.+") || text.matches("\\d+(?:\\.\\d+)+\\.?\\s+.+"));
    }

    private TableCandidate toRecoveredCandidate(
            List<RowSnapshot> group,
            Table table,
            Page page
    ) {
        return new TableCandidate(
                toRecoveredArea(group, table, page),
                toRecoveredRows(group, page)
        );
    }

    private TableArea toRecoveredArea(
            List<RowSnapshot> group,
            Table table,
            Page page
    ) {
        int pageNumber = table.getPageNumber() > 0
                ? table.getPageNumber()
                : page.getPageNumber();

        return new TableArea(
                pageNumber,
                group.stream().mapToDouble(RowSnapshot::top).min().orElseThrow(),
                recoveredGroupLeft(group, page),
                group.stream().mapToDouble(RowSnapshot::bottom).max().orElseThrow(),
                recoveredGroupRight(group, page)
        );
    }

    private double recoveredGroupLeft(
            List<RowSnapshot> group,
            Page page
    ) {
        return group.stream()
                .flatMap(row -> row.contentCells(page).stream())
                .mapToDouble(RectangularTextContainer::getLeft)
                .min()
                .orElseGet(() -> group.stream().mapToDouble(RowSnapshot::left).min().orElseThrow());
    }

    private double recoveredGroupRight(
            List<RowSnapshot> group,
            Page page
    ) {
        return group.stream()
                .flatMap(row -> row.contentCells(page).stream())
                .mapToDouble(RectangularTextContainer::getRight)
                .max()
                .orElseGet(() -> group.stream().mapToDouble(RowSnapshot::right).max().orElseThrow());
    }

    private List<TableRow> toRecoveredRows(
            List<RowSnapshot> group,
            Page page
    ) {
        return group.stream()
                .map(row -> toRecoveredRow(row, page))
                .toList();
    }

    private TableRow toRecoveredRow(
            RowSnapshot row,
            Page page
    ) {
        return new TableRow(row.cells().stream()
                .map(cell -> toRecoveredCell(cell, page))
                .toList());
    }

    private TableCell toRecoveredCell(
            RectangularTextContainer cell,
            Page page
    ) {
        if (isWatermarkOutlierCell(cell, page)) {
            return new TableCell("");
        }

        return toCell(cell);
    }

    private boolean isWatermarkOutlierCell(
            RectangularTextContainer cell,
            Page page
    ) {
        String text = cell.getText() == null ? "" : cell.getText().trim();

        return !text.isBlank()
                && text.length() <= MAX_WATERMARK_OUTLIER_TEXT_LENGTH
                && cell.getLeft() > pageRight(page) * WATERMARK_OUTLIER_LEFT_RATIO;
    }

    private double verticalGap(
            RowSnapshot previous,
            RowSnapshot current
    ) {
        return Math.max(0, current.top() - previous.bottom());
    }

    private double pageRight(Page page) {
        return Math.max(
                page.getRight(),
                page.getWidth()
        );
    }

    private double pageBottom(Page page) {
        return Math.max(
                page.getBottom(),
                page.getHeight()
        );
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

    private record RowSnapshot(
            List<RectangularTextContainer> cells,
            int filledCellCount,
            String normalizedText,
            double top,
            double left,
            double bottom,
            double right
    ) {

        private RowSnapshot(List<RectangularTextContainer> cells) {
            this(
                    List.copyOf(cells),
                    filledCells(cells).size(),
                    normalizeText(cells),
                    top(cells),
                    left(cells),
                    bottom(cells),
                    right(cells)
            );
        }

        private boolean isBlank() {
            return filledCellCount == 0;
        }

        private double height() {
            return bottom - top;
        }

        private int textLength() {
            return normalizedText.length();
        }

        private List<RectangularTextContainer> contentCells(Page page) {
            double outlierLeft = Math.max(page.getRight(), page.getWidth()) * WATERMARK_OUTLIER_LEFT_RATIO;

            return filledCells(cells).stream()
                    .filter(cell -> {
                        String text = cell.getText().trim();

                        return text.length() > MAX_WATERMARK_OUTLIER_TEXT_LENGTH
                                || cell.getLeft() <= outlierLeft;
                    })
                    .toList();
        }

        private static List<RectangularTextContainer> filledCells(List<RectangularTextContainer> cells) {
            return cells.stream()
                    .filter(cell -> cell.getText() != null)
                    .filter(cell -> !cell.getText().trim().isBlank())
                    .toList();
        }

        private static String normalizeText(List<RectangularTextContainer> cells) {
            return filledCells(cells).stream()
                    .map(RectangularTextContainer::getText)
                    .map(text -> text.replace('\n', ' ').replace('\r', ' '))
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .reduce((left, right) -> left + " " + right)
                    .orElse("")
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase();
        }

        private static double top(List<RectangularTextContainer> cells) {
            return filledCells(cells).stream()
                    .mapToDouble(RectangularTextContainer::getTop)
                    .min()
                    .orElse(0);
        }

        private static double left(List<RectangularTextContainer> cells) {
            return filledCells(cells).stream()
                    .mapToDouble(RectangularTextContainer::getLeft)
                    .min()
                    .orElse(0);
        }

        private static double bottom(List<RectangularTextContainer> cells) {
            return filledCells(cells).stream()
                    .mapToDouble(RectangularTextContainer::getBottom)
                    .max()
                    .orElse(0);
        }

        private static double right(List<RectangularTextContainer> cells) {
            return filledCells(cells).stream()
                    .mapToDouble(RectangularTextContainer::getRight)
                    .max()
                    .orElse(0);
        }
    }
}
