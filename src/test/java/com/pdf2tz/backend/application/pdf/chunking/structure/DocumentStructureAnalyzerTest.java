package com.pdf2tz.backend.application.pdf.chunking.structure;

import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredHeadingBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredTextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DocumentStructureAnalyzerTest {

    private final DocumentStructureAnalyzer analyzer = new DocumentStructureAnalyzer();

    @Test
    void buildsPathsWithoutDuplicatingAcceptedHeadingsInBody() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock("""
                        1 НАЗНАЧЕНИЕ
                        Описание изделия
                        1.1 Область применения
                        Область текста
                        1.5 mg/ml
                        """))),
                new ParsedPage(2, List.of(new TextBlock("""
                        2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ
                        Параметры изделия
                        """)))
        ));

        StructuredDocument structured = analyzer.analyze(document);

        assertEquals(6, structured.blocks().size());
        StructuredHeadingBlock firstHeading = assertInstanceOf(
                StructuredHeadingBlock.class,
                structured.blocks().get(0)
        );
        assertEquals("1 НАЗНАЧЕНИЕ", lastHeadingText(firstHeading));

        StructuredTextBlock firstBody = assertInstanceOf(
                StructuredTextBlock.class,
                structured.blocks().get(1)
        );
        assertEquals(
                new SectionPath(List.of(firstHeading.sectionPath().headings().get(0))),
                firstBody.sectionPath()
        );
        assertEquals("Описание изделия", firstBody.text());

        StructuredHeadingBlock secondHeading = assertInstanceOf(
                StructuredHeadingBlock.class,
                structured.blocks().get(2)
        );
        assertEquals(2, secondHeading.sectionPath().headings().size());

        StructuredTextBlock secondBody = assertInstanceOf(
                StructuredTextBlock.class,
                structured.blocks().get(3)
        );
        assertEquals("Область текста\n1.5 mg/ml", secondBody.text());
    }

    @Test
    void keepsTrailingHeadingAsStructuralEvent() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock("""
                        1 НАЗНАЧЕНИЕ
                        Описание
                        2 ПРИЛОЖЕНИЕ
                        """)))
        ));

        StructuredDocument structured = analyzer.analyze(document);

        assertEquals(3, structured.blocks().size());
        assertInstanceOf(StructuredHeadingBlock.class, structured.blocks().get(2));
    }

    @Test
    void treatsCatalogHeadingsAsPeersWhenNoParentPatternExists() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock("""
                        Содержание
                        Первый раздел 2
                        Второй раздел 3
                        Третий раздел 4
                        Четвертый раздел 5
                        """))),
                new ParsedPage(2, List.of(new TextBlock("Первый раздел\nОписание первого раздела."))),
                new ParsedPage(3, List.of(new TextBlock("Второй раздел\nОписание второго раздела."))),
                new ParsedPage(4, List.of(new TextBlock("Третий раздел\nОписание третьего раздела."))),
                new ParsedPage(5, List.of(new TextBlock("Четвертый раздел\nОписание четвертого раздела.")))
        ));

        List<StructuredHeadingBlock> headings = analyzer.analyze(document).blocks().stream()
                .filter(StructuredHeadingBlock.class::isInstance)
                .map(StructuredHeadingBlock.class::cast)
                .toList();

        assertEquals(4, headings.size());
        assertEquals(List.of(1, 1, 1, 1), headings.stream()
                .map(heading -> heading.sectionPath().headings().size())
                .toList());
    }

    @Test
    void infersSingleCatalogHeadingWithoutDroppingFirstPageLine() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock("""
                        Содержание
                        Работа с прибором 2
                        Особые функции 3
                        Совместимые шприцы 4
                        Технические данные 5
                        """))),
                new ParsedPage(2, List.of(new TextBlock("2\nУстановите шприц и включите прибор."))),
                new ParsedPage(3, List.of(new TextBlock("Особые функции\nОписание функций."))),
                new ParsedPage(4, List.of(new TextBlock("Совместимые шприцы\nОписание шприцев."))),
                new ParsedPage(5, List.of(new TextBlock("Технические данные\nОписание параметров.")))
        ));

        StructuredDocument structured = analyzer.analyze(document);
        assertEquals("Работа с прибором", structured.blocks().stream()
                .filter(StructuredHeadingBlock.class::isInstance)
                .map(StructuredHeadingBlock.class::cast)
                .findFirst().orElseThrow().sectionPath().headings().get(0).text());
        assertEquals("2\nУстановите шприц и включите прибор.",
                structured.blocks().stream()
                        .filter(StructuredTextBlock.class::isInstance)
                        .map(StructuredTextBlock.class::cast)
                        .filter(block -> block.pageRange().startPageNumber() == 2)
                        .findFirst().orElseThrow().text());
    }

    @Test
    void doesNotInferHeadingBeforeCatalogPageNumbersAreVerified() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock("""
                        Содержание
                        Работа с прибором 2
                        Особые функции 3
                        Совместимые шприцы 4
                        Технические данные 5
                        """))),
                new ParsedPage(2, List.of(new TextBlock("II\nОтветственность изготовителя."))),
                new ParsedPage(3, List.of(new TextBlock("III\nИнформация об изделии."))),
                new ParsedPage(4, List.of(new TextBlock("IV\nОписание изделия."))),
                new ParsedPage(5, List.of(new TextBlock("V\nМеры безопасности.")))
        ));

        assertEquals(0, analyzer.analyze(document).blocks().stream()
                .filter(StructuredHeadingBlock.class::isInstance)
                .count());
    }

    @Test
    void resetsSectionPathWhenAnotherContentsRegionStartsAnAppendedManual() {
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock("1 НАЗНАЧЕНИЕ\nОписание."))),
                new ParsedPage(2, List.of(new TextBlock("1.1 Применение\nПродолжение."))),
                new ParsedPage(3, List.of(new TextBlock("""
                        Содержание
                        Новый раздел ........ 4
                        Эксплуатация ........ 5
                        Обслуживание ........ 6
                        Утилизация ........ 7
                        """))),
                new ParsedPage(4, List.of(new TextBlock(
                        "Введение к новой инструкции без явного заголовка.")))
        ));

        StructuredDocument structured = analyzer.analyze(document);

        StructuredTextBlock introduction = structured.blocks().stream()
                .filter(StructuredTextBlock.class::isInstance)
                .map(StructuredTextBlock.class::cast)
                .filter(block -> block.pageRange().startPageNumber() == 4)
                .findFirst().orElseThrow();
        assertEquals(SectionPath.root(), introduction.sectionPath());
        assertEquals("Введение к новой инструкции без явного заголовка.",
                introduction.text());
    }

    @Test
    void doesNotPromoteAcceptedTableCellPrefixToSectionHeading() {
        ParsedTable table = new ParsedTable(List.of(new TableFragment(
                new TableArea(2, 100, 30, 400, 500),
                List.of(new TableRow(List.of(
                        new TableCell("I. Mucous Membrane Procedures Examples: Oral, Nasal"),
                        new TableCell("Medical risks and benefits should be discussed")
                )), new TableRow(List.of(
                        new TableCell("DESCRIBING CELL SAVER 5+ ERROR CODES details"),
                        new TableCell("Another table value")
                )))
        )));
        ParsedDocument document = new ParsedDocument(List.of(
                new ParsedPage(1, List.of(new TextBlock(
                        "GENERAL INFORMATION ABOUT DEVICE\nReference text."))),
                new ParsedPage(2, List.of(
                        new TextBlock("""
                                P/N 53063-30, Manual revision: B A-5
                                Providing Reference Information
                                I. Mucous Membrane Procedures
                                Examples: Oral, Nasal
                                """),
                        new TableBlock(table)
                )),
                new ParsedPage(3, List.of(new TextBlock("""
                        P/N 53063-30, Manual revision: B A-7
                        Providing Reference Information
                        DESCRIBING CELL SAVER 5+ ERROR CODES
                        The following table lists these error codes.
                        """)))
        ));

        List<String> headings = analyzer.analyze(document).blocks().stream()
                .filter(StructuredHeadingBlock.class::isInstance)
                .map(StructuredHeadingBlock.class::cast)
                .map(this::lastHeadingText)
                .toList();

        assertEquals(List.of(
                "GENERAL INFORMATION ABOUT DEVICE",
                "DESCRIBING CELL SAVER 5+ ERROR CODES"
        ), headings);
    }

    private String lastHeadingText(StructuredHeadingBlock block) {
        List<com.pdf2tz.backend.application.pdf.model.structure.SectionHeading> headings =
                block.sectionPath().headings();
        return headings.get(headings.size() - 1).text();
    }
}
