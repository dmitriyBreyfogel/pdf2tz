package com.pdf2tz.backend.application.pdf.chunking.structure;

import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredHeadingBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredTextBlock;
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

    private String lastHeadingText(StructuredHeadingBlock block) {
        List<com.pdf2tz.backend.application.pdf.model.structure.SectionHeading> headings =
                block.sectionPath().headings();
        return headings.get(headings.size() - 1).text();
    }
}
