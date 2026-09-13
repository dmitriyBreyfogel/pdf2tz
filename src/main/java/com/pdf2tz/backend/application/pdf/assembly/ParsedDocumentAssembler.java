package com.pdf2tz.backend.application.pdf.assembly;

import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextPage;
import com.pdf2tz.backend.application.pdf.model.document.DocumentBlock;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedPage;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Собирает готовый документ из очищенного постраничного текста и структурированных таблиц.
 *
 * <p>Каждая непустая очищенная страница превращается в {@link TextBlock}, а
 * целостные таблицы добавляются на страницу, с которой они начинаются. Если
 * таблица переносится на несколько страниц, она всё равно остаётся одним
 * {@link TableBlock}, потому что логически это одна таблица документа.</p>
 */
@Component
public class ParsedDocumentAssembler {

    /**
     * Преобразует очищенный документ в готовую блочную модель.
     *
     * <p>Метод сохраняет все страницы исходного очищенного документа, включая
     * пустые. Это важно, чтобы ссылки на номера страниц оставались стабильными
     * при последующей сборке чанков и ответов LLM.</p>
     *
     * @param cleanedTextDocument очищенный текстовый слой PDF-документа
     * @return готовый документ с текстовыми блоками
     */
    public ParsedDocument assemble(CleanedTextDocument cleanedTextDocument) {
        return assemble(cleanedTextDocument, List.of());
    }

    /**
     * Преобразует очищенный документ и найденные таблицы в готовую блочную модель.
     *
     * <p>Пока текстовая модель не хранит координаты строк и абзацев, assembler
     * не пытается встроить таблицу внутрь текста страницы по точному месту в PDF.
     * Поэтому текстовый блок страницы добавляется первым, а табличные блоки этой
     * страницы - после него в порядке расположения таблиц сверху вниз.</p>
     *
     * @param cleanedTextDocument очищенный текстовый слой PDF-документа
     * @param tables целостные таблицы документа
     * @return готовый документ с текстовыми и табличными блоками
     */
    public ParsedDocument assemble(
            CleanedTextDocument cleanedTextDocument,
            List<ParsedTable> tables
    ) {
        Objects.requireNonNull(cleanedTextDocument, "Cleaned text document must not be null");
        Objects.requireNonNull(tables, "Parsed tables must not be null");

        Map<Integer, List<ParsedTable>> tablesByStartPage = tables.stream()
                .map(table -> Objects.requireNonNull(table, "Parsed table must not be null"))
                .sorted(this::compareTablesByReadingOrder)
                .collect(Collectors.groupingBy(ParsedTable::startPageNumber));

        List<ParsedPage> pages = cleanedTextDocument.pages().stream()
                .map(page -> new ParsedPage(
                        page.pageNumber(),
                        buildPageBlocks(page, tablesByStartPage.getOrDefault(page.pageNumber(), List.of()))
                ))
                .toList();

        return new ParsedDocument(pages);
    }

    private List<DocumentBlock> buildPageBlocks(
            CleanedTextPage page,
            List<ParsedTable> tables
    ) {
        List<DocumentBlock> blocks = new ArrayList<>();
        String text = Objects.requireNonNull(page.text(), "Cleaned text page text must not be null")
                .trim();

        if (!text.isBlank()) {
            blocks.add(new TextBlock(text));
        }

        tables.stream()
                .map(TableBlock::new)
                .forEach(blocks::add);

        return List.copyOf(blocks);
    }

    private int compareTablesByReadingOrder(
            ParsedTable first,
            ParsedTable second
    ) {
        return Comparator.comparingInt(ParsedTable::startPageNumber)
                .thenComparingDouble(table -> table.fragments().get(0).area().top())
                .thenComparingDouble(table -> table.fragments().get(0).area().left())
                .compare(first, second);
    }
}
