package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableQualityFilterTest {

    private final TableQualityFilter filter = new TableQualityFilter();

    @Test
    void removesDiagramLetterMarkers() {
        TableFragment fragment = fragment(
                row("A BC", "C")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesSparseSchemeLabelsWithNumbers() {
        TableFragment fragment = fragment(
                row("D E 209", ""),
                row("", "2")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesPlainTextExtractedAsSingleColumnTable() {
        TableFragment fragment = fragment(
                row("Внимание"),
                row("Перед использованием изделия ознакомьтесь с инструкцией по эксплуатации."),
                row("Не допускается эксплуатация изделия при повреждении корпуса.")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void keepsParameterValueTable() {
        TableFragment fragment = fragment(
                row("Параметр", "Значение"),
                row("Скорость потока", "10 мл/ч"),
                row("Масса", "2 кг")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertEquals(List.of(fragment), result);
    }

    @Test
    void keepsAbbreviationDefinitionTable() {
        TableFragment fragment = fragment(
                row("ИВЛ", "Искусственная вентиляция легких"),
                row("ВВЛ", "Вспомогательная вентиляция легких"),
                row("ПДУ", "Пульт дистанционного управления")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertEquals(List.of(fragment), result);
    }

    @Test
    void keepsMeaningfulSingleRowKeyValueTable() {
        TableFragment fragment = fragment(
                row("Объём", "250 мл")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertEquals(List.of(fragment), result);
    }

    @Test
    void removesSingleRowTextWithoutValuePattern() {
        TableFragment fragment = fragment(
                row("Внимание", "Перед использованием изделия ознакомьтесь с инструкцией")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void rejectsNullFragmentList() {
        assertThrows(
                NullPointerException.class,
                () -> filter.filter(null)
        );
    }

    private TableFragment fragment(TableRow... rows) {
        return new TableFragment(
                new TableArea(1, 10, 20, 100, 200),
                Arrays.asList(rows)
        );
    }

    private TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }
}
