package com.pdf2tz.backend.application.pdf.assembly;

import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextPage;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTableOverlapCleanerTest {

    private final TextTableOverlapCleaner cleaner = new TextTableOverlapCleaner();

    @Test
    void removesTableRowsFromTextOnSamePage() {
        CleanedTextPage page = new CleanedTextPage(1, """
                Перед таблицей
                Параметр Значение
                Скорость 10 мл/ч
                После таблицы
                """);
        ParsedTable table = table(fragment(
                area(1, 100, 40),
                row("Параметр", "Значение"),
                row("Скорость", "10 мл/ч")
        ));

        String result = cleaner.removeOverlaps(page, List.of(table));

        assertEquals("""
                Перед таблицей
                После таблицы""", result);
    }

    @Test
    void removesRowsWhenCellsAreGluedInTextLayer() {
        CleanedTextPage page = new CleanedTextPage(1, """
                Список сокращений
                И В ЛИскусственная вентиляция легких
                Конец списка
                """);
        ParsedTable table = table(fragment(
                area(1, 100, 40),
                row("ИВЛ", "Искусственная вентиляция легких")
        ));

        String result = cleaner.removeOverlaps(page, List.of(table));

        assertEquals("""
                Список сокращений
                Конец списка""", result);
    }

    @Test
    void keepsTextWithOnlyPartialTableWords() {
        CleanedTextPage page = new CleanedTextPage(1, """
                Параметр вентиляции имеет важное значение для пациента.
                """);
        ParsedTable table = table(fragment(
                area(1, 100, 40),
                row("Параметр", "Значение")
        ));

        String result = cleaner.removeOverlaps(page, List.of(table));

        assertEquals("Параметр вентиляции имеет важное значение для пациента.", result);
    }

    @Test
    void doesNotRemoveRowsFromDifferentPage() {
        CleanedTextPage page = new CleanedTextPage(1, """
                Параметр Значение
                Скорость 10 мл/ч
                """);
        ParsedTable table = table(fragment(
                area(2, 100, 40),
                row("Параметр", "Значение"),
                row("Скорость", "10 мл/ч")
        ));

        String result = cleaner.removeOverlaps(page, List.of(table));

        assertEquals("""
                Параметр Значение
                Скорость 10 мл/ч""", result);
    }

    @Test
    void removesOnlyRowsFromCurrentFragmentPageForMultiPageTable() {
        ParsedTable table = table(
                fragment(
                        area(1, 100, 40),
                        row("Параметр", "Значение"),
                        row("Скорость", "10 мл/ч")
                ),
                fragment(
                        area(2, 100, 40),
                        row("Объём", "250 мл")
                )
        );
        CleanedTextPage firstPage = new CleanedTextPage(1, """
                Первая страница
                Скорость 10 мл/ч
                Объём 250 мл
                """);
        CleanedTextPage secondPage = new CleanedTextPage(2, """
                Вторая страница
                Скорость 10 мл/ч
                Объём 250 мл
                """);

        String firstResult = cleaner.removeOverlaps(firstPage, List.of(table));
        String secondResult = cleaner.removeOverlaps(secondPage, List.of(table));

        assertEquals("""
                Первая страница
                Объём 250 мл""", firstResult);
        assertEquals("""
                Вторая страница
                Скорость 10 мл/ч""", secondResult);
    }

    @Test
    void removesOnlyLongSingleCellRowsByStrictMatch() {
        CleanedTextPage page = new CleanedTextPage(1, """
                Полное описание режима вентиляции пациента
                Описание режима вентиляции встречается в обычном абзаце и не должно удаляться.
                """);
        ParsedTable table = table(fragment(
                area(1, 100, 40),
                row("Полное описание режима вентиляции пациента")
        ));

        String result = cleaner.removeOverlaps(page, List.of(table));

        assertEquals("Описание режима вентиляции встречается в обычном абзаце и не должно удаляться.", result);
    }

    private ParsedTable table(TableFragment... fragments) {
        return new ParsedTable(Arrays.asList(fragments));
    }

    private TableFragment fragment(
            TableArea area,
            TableRow... rows
    ) {
        return new TableFragment(area, Arrays.asList(rows));
    }

    private TableArea area(
            int pageNumber,
            double top,
            double left
    ) {
        return new TableArea(pageNumber, top, left, top + 100, left + 200);
    }

    private TableRow row(String... values) {
        return new TableRow(Arrays.stream(values)
                .map(TableCell::new)
                .toList());
    }
}
