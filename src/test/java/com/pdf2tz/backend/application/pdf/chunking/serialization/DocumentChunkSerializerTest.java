package com.pdf2tz.backend.application.pdf.chunking.serialization;

import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentChunkSerializerTest {

    private final DocumentChunkSerializer documentSerializer = new DocumentChunkSerializer();
    private final TableTextSerializer tableSerializer = new TableTextSerializer();

    @Test
    void serializesSectionPathAndBody() {
        SectionPath path = new SectionPath(List.of(
                new SectionHeading(1, "5 Эксплуатация", 3),
                new SectionHeading(2, "5.1 Подготовка", 4),
                new SectionHeading(7, "Глубокий раздел", 5)
        ));

        assertEquals(
                "# 5 Эксплуатация\n## 5.1 Подготовка\n###### Глубокий раздел",
                documentSerializer.serializeSectionPath(path)
        );
        assertEquals(
                "# 5 Эксплуатация\n## 5.1 Подготовка\n###### Глубокий раздел\n\nТекст",
                documentSerializer.serialize(path, List.of("Текст"))
        );
    }

    @Test
    void serializesTableCellsWithoutLosingColumnsOrDelimiters() {
        ParsedTable table = new ParsedTable(List.of(
                new TableFragment(
                        new TableArea(1, 10, 10, 30, 100),
                        List.of(new TableRow(List.of(
                                new TableCell("A|B"),
                                new TableCell("line 1\nline 2"),
                                new TableCell("\\")
                        )))
                )
        ));

        assertEquals(
                "[TABLE]\n| A\\|B | line 1<br>line 2 | \\\\ |\n[/TABLE]",
                tableSerializer.serialize(table)
        );
    }

    @Test
    void serializesRootBodyWithoutArtificialHeading() {
        assertEquals("Текст", documentSerializer.serialize(SectionPath.root(), List.of("Текст")));
    }
}
