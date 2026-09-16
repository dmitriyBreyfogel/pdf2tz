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
    void removesTableOfContentsExtractedAsTable() {
        TableFragment fragment = fragment(
                row("Table of Contents"),
                row("Preface ii"),
                row("Limited Warranty iii"),
                row("Section One 1"),
                row("Troubleshooting 42")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesSingleColumnTableWithCollapsedHeaders() {
        TableFragment fragment = fragment(
                row("No п/п Наименование изделия Децимальный номер Количество"),
                row("1 Секция консольная 12345 2"),
                row("2 Стойка монтажная 67890 1")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesMultiColumnFragmentWithoutFilledColumnPairs() {
        TableFragment fragment = fragment(
                row("", "13 СПИСОК ПРИНЯТЫХ СОКРАЩЕНИЙ И УСЛОВНЫХ ЗНАКОВ"),
                row("", "ИВЛ, CMV - искусственная вентиляция легких;"),
                row("", "ВЧ - режим высокочастотной ИВЛ;")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesSparseMultiColumnListExtractedAsTable() {
        TableFragment fragment = fragment(
                row("", "13 СПИСОК ПРИНЯТЫХ СОКРАЩЕНИЙ И УСЛОВНЫХ ЗНАКОВ"),
                row("", "ИВЛ, CMV - искусственная вентиляция легких;"),
                row("", "ВЧ - режим высокочастотной ИВЛ;"),
                row("(или,", "соответственно - отношение времени вдоха ко времени выдоха). Таблица"),
                row("соответствия на боковой панели аппарата;", ""),
                row("", "HFPPV - высокочастотная ИВЛ с положительным давлением;")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesHeaderValueFragmentSplitAcrossRows() {
        TableFragment fragment = fragment(
                row("Наименование параметра", "Значение"),
                row("Суммарная мощность электроаппаратуры", ""),
                row("", "3,5")
        );

        List<TableFragment> result = filter.filter(List.of(fragment));

        assertTrue(result.isEmpty());
    }

    @Test
    void removesSparseBrokenGrid() {
        TableFragment fragment = fragment(
                row("Обозначение", "", "Наименование", "Примечание"),
                row("ГАКЕ 55.00.00", "", "Система клапанная", "в комплекте"),
                row("", "", "Составные части", ""),
                row("ГАКЕ 55.00.00 -02", "", "", ""),
                row("ГАКЕ 55.00.00 -03", "", "", "")
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
