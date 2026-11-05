package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.assembly.ParsedDocumentAssembler;
import com.pdf2tz.backend.application.pdf.assembly.TextTableOverlapCleaner;
import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.document.TableBlock;
import com.pdf2tz.backend.application.pdf.model.document.TextBlock;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.table.PdfTableParsingService;
import com.pdf2tz.backend.application.pdf.table.TableAssembler;
import com.pdf2tz.backend.application.pdf.table.TableCandidateSelector;
import com.pdf2tz.backend.application.pdf.table.TableNormalizer;
import com.pdf2tz.backend.application.pdf.table.TableQualityFilter;
import com.pdf2tz.backend.infrastructure.pdf.TikaPdfReader;
import com.pdf2tz.backend.infrastructure.pdf.table.TabulaPdfTableExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Регрессии на исходных инструкциях, не включённых в репозиторий.
 * Для запуска задаётся PDF_INSTRUCTION_DIRECTORY с путём к каталогу PDF.
 * Проверяются конкретные ошибки, а не общее количество найденных таблиц.
 */
@EnabledIfEnvironmentVariable(named = "PDF_INSTRUCTION_DIRECTORY", matches = ".+")
class PdfInstructionRegressionTest {

    private final TextCleaner cleaner = new TextCleaner();
    private final PdfParsingPipeline pipeline = new PdfParsingPipeline(
            new TikaPdfReader(), new DocumentNoiseProfileBuilder(), cleaner,
            new PdfTableParsingService(new TabulaPdfTableExtractor(), new TableCandidateSelector(),
                    new TableNormalizer(cleaner), new TableQualityFilter(), new TableAssembler()),
            new ParsedDocumentAssembler(new TextTableOverlapCleaner())
    );

    @Test
    void preservesWordsWhileRemovingKnownWatermark() throws Exception {
        var document = pipeline.extractText(content("1372805.pdf"));
        assertEquals(78, document.pages().size());
        assertTrue(document.pages().get(15).text().contains(".магистрали"));
        assertTrue(document.pages().get(30).text().contains(".медицинское"));
        // В текстовом слое шестой страницы есть только watermark, содержимое — изображение.
        assertTrue(document.pages().get(5).text().isBlank());
    }

    @Test
    void keepsFullTextAndRejectsKnownDiagramTables() throws Exception {
        byte[] content = content("1373967.pdf");
        var text = pipeline.extractText(content);
        var document = pipeline.parse(content);
        var tables = tables(document);
        assertEquals(250, document.pages().size());
        assertTrue(text.pages().get(107).text().contains("От 15 до 70"));
        assertTrue(text.pages().get(107).text().contains("Заводские настройки"));
        assertTrue(tables.stream().noneMatch(table -> List.of(35, 45, 47, 64, 83).contains(table.startPageNumber())));
        assertTrue(tables.stream().anyMatch(table -> table.startPageNumber() == 108));
        assertTrue(tables.stream().anyMatch(table -> table.startPageNumber() == 202 && table.endPageNumber() == 203));
        assertTrue(document.pages().get(82).blocks().stream()
                .filter(TextBlock.class::isInstance).map(TextBlock.class::cast)
                .anyMatch(block -> block.text().contains("Новорожденные")));
    }

    @Test
    void separatesNewErrorCodeTableAndRemovesOnlyRepeatedHeader() throws Exception {
        var tables = tables(pipeline.parse(content("1373694.pdf")));
        assertFalse(tables.stream().anyMatch(table -> table.startPageNumber() <= 144 && table.endPageNumber() >= 145));
        ParsedTable errors = tables.stream().filter(table -> table.startPageNumber() == 145).findFirst().orElseThrow();
        assertEquals(148, errors.endPageNumber());
        assertEquals(1, errors.rows().stream().filter(row -> row.cells().get(0).text().equals("Code")).count());
        assertEquals(4, errors.fragments().size());
    }

    private byte[] content(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getenv("PDF_INSTRUCTION_DIRECTORY"), name));
    }

    private List<ParsedTable> tables(ParsedDocument document) {
        return document.pages().stream().flatMap(page -> page.blocks().stream())
                .filter(TableBlock.class::isInstance).map(TableBlock.class::cast)
                .map(TableBlock::table).toList();
    }
}
