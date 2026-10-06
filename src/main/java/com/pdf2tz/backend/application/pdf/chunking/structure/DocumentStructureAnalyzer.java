package com.pdf2tz.backend.application.pdf.chunking.structure;

import com.pdf2tz.backend.application.pdf.model.document.DocumentBlock;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocumentBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredHeadingBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredTableBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredTextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.table.TableTextMatch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Восстанавливает консервативную иерархию разделов поверх {@link ParsedDocument}.
 *
 * <p>Анализатор не изменяет исходный документ и не знает о token budget.
 * Текстовые строки, которые не прошли детектор заголовков, остаются body-текстом.
 * Таблицы сохраняются как отдельные логические блоки и получают только тот путь,
 * который можно обосновать доступным постраничным контекстом. Повторное
 * оглавление сбрасывает путь раздела в составных PDF.</p>
 */
@Service
public class DocumentStructureAnalyzer {

    private final HeadingDetector headingDetector;
    private final HeadingLevelResolver headingLevelResolver;
    private final TableSectionPathResolver tableSectionPathResolver;

    public DocumentStructureAnalyzer() {
        this.headingDetector = new HeadingDetector();
        this.headingLevelResolver = new HeadingLevelResolver();
        this.tableSectionPathResolver = new TableSectionPathResolver();
    }

    /**
     * Преобразует блочную модель PDF в структурную последовательность.
     *
     * @param document распарсенный документ
     * @return структурированный документ в порядке чтения
     */
    public StructuredDocument analyze(ParsedDocument document) {
        Objects.requireNonNull(document, "Parsed document must not be null");

        List<SourceLine> sourceLines = sourceLines(document);
        ContentsRegionDetector.ContentsProfile contentsProfile =
                new ContentsRegionDetector().analyze(sourceLines);
        List<HeadingCandidate> acceptedHeadings = headingDetector.detect(
                sourceLines, contentsProfile, tableCellLineOrders(document, sourceLines));
        Set<Integer> contentsPages = sourceLines.stream()
                .filter(line -> contentsProfile.tocLineOrders().contains(line.order()))
                .map(SourceLine::pageNumber)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean hasTopLevelAnchors = acceptedHeadings.stream()
                .filter(candidate -> candidate.scheme() == HeadingScheme.TITLE_CASE)
                .filter(HeadingCandidate::repeatedOnSourcePage)
                .count() >= 2;
        Map<Integer, HeadingCandidate> headingsByOrder = acceptedHeadings.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        candidate -> candidate.sourceLine().order(),
                        candidate -> candidate
                ));

        return assemble(document, headingsByOrder, hasTopLevelAnchors, contentsPages);
    }

    private List<SourceLine> sourceLines(ParsedDocument document) {
        List<SourceLine> lines = new ArrayList<>();
        int order = 0;

        for (ParsedPage page : document.pages()) {
            int blockIndex = 0;
            for (DocumentBlock block : page.blocks()) {
                if (block instanceof TextBlock textBlock) {
                    int lineIndex = 0;
                    for (String line : textBlock.text().lines().toList()) {
                        if (!line.isBlank()) {
                            lines.add(new SourceLine(
                                    page.pageNumber(),
                                    blockIndex,
                                    lineIndex,
                                    order++,
                                    line
                            ));
                        }
                        lineIndex++;
                    }
                }
                blockIndex++;
            }
        }

        return List.copyOf(lines);
    }

    /**
     * Отмечает строки, являющиеся началом более длинной ячейки принятой таблицы
     * на той же физической странице. Такие обрывки текстового слоя могут выглядеть
     * как самостоятельные заголовки, хотя в таблице за ними следует продолжение.
     * Совпадение с ячейкой другой страницы и полное совпадение не используются:
     * последнее уже обрабатывает удаление уверенных дублей при сборке ParsedDocument.
     */
    private Set<Integer> tableCellLineOrders(
            ParsedDocument document,
            List<SourceLine> lines
    ) {
        Map<Integer, List<String>> cellsByPage = new HashMap<>();
        document.pages().stream()
                .flatMap(page -> page.blocks().stream())
                .filter(TableBlock.class::isInstance)
                .map(TableBlock.class::cast)
                .flatMap(block -> block.table().fragments().stream())
                .forEach(fragment -> fragment.rows().stream()
                        .flatMap(row -> row.cells().stream())
                        .map(cell -> TableTextMatch.compact(cell.text()))
                        .filter(cell -> !cell.isBlank())
                        .forEach(cell -> cellsByPage
                                .computeIfAbsent(fragment.pageNumber(), ignored -> new ArrayList<>())
                                .add(cell)));

        return lines.stream()
                .filter(line -> {
                    String text = TableTextMatch.compact(line.text());
                    return !text.isBlank() && cellsByPage
                            .getOrDefault(line.pageNumber(), List.of())
                            .stream()
                            .anyMatch(cell -> cell.length() > text.length()
                                    && cell.startsWith(text));
                })
                .map(SourceLine::order)
                .collect(Collectors.toUnmodifiableSet());
    }

    private StructuredDocument assemble(
            ParsedDocument document,
            Map<Integer, HeadingCandidate> headingsByOrder,
            boolean hasTopLevelAnchors,
            Set<Integer> contentsPages
    ) {
        List<StructuredDocumentBlock> result = new ArrayList<>();
        SectionPath currentPath = SectionPath.root();
        int sourceOrder = 0;

        for (ParsedPage page : document.pages()) {
            // Повторное оглавление в сборном PDF обозначает новую границу документа.
            // Без сброса путь последнего раздела предыдущей инструкции «протечёт» дальше.
            if (contentsPages.contains(page.pageNumber())) {
                currentPath = SectionPath.root();
            }
            SectionPath pathBeforePage = currentPath;
            Set<SectionPath> pathsOnPage = new LinkedHashSet<>();
            TextAccumulator textAccumulator = new TextAccumulator();

            int blockIndex = 0;
            for (DocumentBlock block : page.blocks()) {
                if (block instanceof TextBlock textBlock) {
                    int lineIndex = 0;
                    for (String line : textBlock.text().lines().toList()) {
                        if (!line.isBlank()) {
                            HeadingCandidate heading = headingsByOrder.get(sourceOrder++);
                            if (heading != null) {
                                if (isDuplicateCurrentHeading(currentPath, heading)) {
                                    if (heading.inferredFromContents()) {
                                        textAccumulator.add(line, page.pageNumber(), currentPath);
                                        pathsOnPage.add(currentPath);
                                    }
                                    continue;
                                }
                                flushText(textAccumulator, result);
                                currentPath = advancePath(
                                        currentPath,
                                        heading,
                                        headingsByOrder.values(),
                                        hasTopLevelAnchors
                                );
                                result.add(new StructuredHeadingBlock(currentPath));
                                pathsOnPage.add(currentPath);
                                if (heading.inferredFromContents()) {
                                    textAccumulator.add(line, page.pageNumber(), currentPath);
                                }
                            } else {
                                textAccumulator.add(line, page.pageNumber(), currentPath);
                                pathsOnPage.add(currentPath);
                            }
                        }
                        lineIndex++;
                    }
                }
                blockIndex++;
            }

            flushText(textAccumulator, result);
            SectionPath pathAfterPage = currentPath;
            appendTables(
                    page,
                    pathsOnPage,
                    pathBeforePage,
                    pathAfterPage,
                    result
            );
        }

        return new StructuredDocument(result);
    }

    private SectionPath advancePath(
            SectionPath currentPath,
            HeadingCandidate candidate,
            java.util.Collection<HeadingCandidate> acceptedCandidates,
            boolean hasTopLevelAnchors
    ) {
        boolean letterAsNested = acceptedCandidates.stream()
                .filter(other -> other.scheme() == HeadingScheme.LETTER)
                .count() >= 2
                && !currentPath.isRoot();
        boolean titleCaseNested = candidate.scheme() == HeadingScheme.TITLE_CASE
                && hasTopLevelAnchors
                && !currentPath.isRoot()
                && !candidate.repeatedOnSourcePage();

        SectionHeading heading = headingLevelResolver.toHeading(
                candidate,
                new HeadingLevelResolver.ListContext(letterAsNested, titleCaseNested)
        );
        List<SectionHeading> headings = new ArrayList<>(currentPath.headings());
        headings.removeIf(existing -> existing.level() >= heading.level());
        headings.add(heading);
        return new SectionPath(headings);
    }

    private boolean isDuplicateCurrentHeading(
            SectionPath currentPath,
            HeadingCandidate candidate
    ) {
        if (currentPath.isRoot()) {
            return false;
        }

        String candidateTitle = normalizeHeading(candidate.headingText());
        return currentPath.headings().stream()
                .anyMatch(heading -> normalizeHeading(heading.text()).equals(candidateTitle));
    }

    private String normalizeHeading(String text) {
        return text
                .replaceFirst("^\\s*\\d+(?:\\.\\d+)*[.)]?\\s+", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private void appendTables(
            ParsedPage page,
            Set<SectionPath> pathsOnPage,
            SectionPath pathBeforePage,
            SectionPath pathAfterPage,
            List<StructuredDocumentBlock> result
    ) {
        List<ParsedTable> tables = page.blocks().stream()
                .filter(TableBlock.class::isInstance)
                .map(TableBlock.class::cast)
                .map(TableBlock::table)
                .toList();
        SectionPath tablePath = tableSectionPathResolver.resolve(
                pathBeforePage,
                List.copyOf(pathsOnPage),
                pathAfterPage
        );
        tables.stream()
                .map(table -> new StructuredTableBlock(tablePath, table))
                .forEach(result::add);
    }

    private void flushText(
            TextAccumulator accumulator,
            List<StructuredDocumentBlock> result
    ) {
        accumulator.flush().ifPresent(result::add);
    }

    private static final class TextAccumulator {

        private final StringBuilder text = new StringBuilder();
        private SectionPath sectionPath;
        private PageRange pageRange;

        void add(String line, int pageNumber, SectionPath path) {
            if (text.isEmpty()) {
                sectionPath = path;
                pageRange = PageRange.single(pageNumber);
            } else if (!sectionPath.equals(path)) {
                throw new IllegalStateException("Text accumulator path changed before flush");
            } else {
                pageRange = pageRange.merge(PageRange.single(pageNumber));
            }

            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(line.trim());
        }

        java.util.Optional<StructuredTextBlock> flush() {
            if (text.isEmpty()) {
                return java.util.Optional.empty();
            }

            StructuredTextBlock result = new StructuredTextBlock(
                    sectionPath,
                    pageRange,
                    text.toString()
            );
            text.setLength(0);
            sectionPath = null;
            pageRange = null;
            return java.util.Optional.of(result);
        }
    }
}
