package com.pdf2tz.backend.application.pdf.model.structure;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureModelTest {

    @Test
    void pageRangeMergesAndValidates() {
        assertEquals(new PageRange(2, 5), new PageRange(2, 3).merge(new PageRange(4, 5)));
        assertThrows(IllegalArgumentException.class, () -> new PageRange(2, 1));
    }

    @Test
    void sectionPathFindsCommonPrefixAndCopiesInput() {
        SectionHeading parent = new SectionHeading(1, "1 Operation", 2);
        SectionHeading child = new SectionHeading(2, "1.1 Setup", 3);
        List<SectionHeading> source = new ArrayList<>(List.of(parent, child));
        SectionPath path = new SectionPath(source);
        source.clear();

        assertEquals(2, path.headings().size());
        assertEquals(
                new SectionPath(List.of(parent)),
                path.commonPrefix(new SectionPath(List.of(parent, new SectionHeading(2, "1.2", 4))))
        );
    }

    @Test
    void tableBlockUsesLogicalTablePageRange() {
        ParsedTable table = new ParsedTable(List.of(
                new TableFragment(
                        new TableArea(3, 10, 10, 30, 100),
                        List.of(new TableRow(List.of(new TableCell("a"))))
                ),
                new TableFragment(
                        new TableArea(4, 10, 10, 30, 100),
                        List.of(new TableRow(List.of(new TableCell("b"))))
                )
        ));

        StructuredTableBlock block = new StructuredTableBlock(SectionPath.root(), table);
        assertEquals(new PageRange(3, 4), block.pageRange());
    }
}
