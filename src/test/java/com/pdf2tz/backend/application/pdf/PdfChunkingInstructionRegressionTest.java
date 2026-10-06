package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.assembly.ParsedDocumentAssembler;
import com.pdf2tz.backend.application.pdf.assembly.TextTableOverlapCleaner;
import com.pdf2tz.backend.application.pdf.chunking.ChunkingProperties;
import com.pdf2tz.backend.application.pdf.chunking.StructuredDocumentChunker;
import com.pdf2tz.backend.application.pdf.chunking.serialization.DocumentChunkSerializer;
import com.pdf2tz.backend.application.pdf.chunking.serialization.TableTextSerializer;
import com.pdf2tz.backend.application.pdf.chunking.table.TableChunkSplitter;
import com.pdf2tz.backend.application.pdf.chunking.table.TableHeaderDetector;
import com.pdf2tz.backend.application.pdf.chunking.structure.DocumentStructureAnalyzer;
import com.pdf2tz.backend.application.pdf.chunking.text.TextSegmentSplitter;
import com.pdf2tz.backend.application.pdf.retrieval.LexicalChunkRanker;
import com.pdf2tz.backend.application.pdf.retrieval.PdfContextAssembler;
import com.pdf2tz.backend.application.pdf.retrieval.RetrievalProperties;
import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.table.PdfTableParsingService;
import com.pdf2tz.backend.application.pdf.table.TableAssembler;
import com.pdf2tz.backend.application.pdf.table.TableCandidateSelector;
import com.pdf2tz.backend.application.pdf.table.TableNormalizer;
import com.pdf2tz.backend.application.pdf.table.TableQualityFilter;
import com.pdf2tz.backend.infrastructure.pdf.TikaPdfReader;
import com.pdf2tz.backend.infrastructure.pdf.table.TabulaPdfTableExtractor;
import com.pdf2tz.backend.infrastructure.text.IcuTextBoundaryAdapter;
import com.pdf2tz.backend.infrastructure.text.JTokkitTokenizerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression-проверки chunking на PDF вне репозитория.
 *
 * <p>Запускаются только при заданном {@code PDF_INSTRUCTION_DIRECTORY}.</p>
 */
@EnabledIfEnvironmentVariable(named = "PDF_INSTRUCTION_DIRECTORY", matches = ".+")
class PdfChunkingInstructionRegressionTest {

    @Test
    void retrievesMaintenanceIntervalFromLargeInstruction() throws Exception {
        var context = contextPipeline().prepare(content("1373967.pdf"),
                "Редукторы давления: через сколько лет общий осмотр и замена?");

        assertTrue(context.contextAvailable());
        assertTrue(context.selectedChunks().stream().anyMatch(chunk ->
                chunk.content().contains("Через 6 лет")
                        && chunk.pageRange().startPageNumber() <= 203
                        && chunk.pageRange().endPageNumber() >= 202));
        assertTrue(context.estimatedTokenCount() <= 8192);
    }

    @Test
    void retrievesErrorCodesFromMultiPageTable() throws Exception {
        var context = contextPipeline().prepare(content("1373694.pdf"),
                "Cell Saver error codes for clamp and reservoir red line");

        assertTrue(context.contextAvailable());
        assertTrue(context.selectedChunks().stream().anyMatch(chunk ->
                chunk.content().contains("Clamp error")
                        && chunk.pageRange().startPageNumber() <= 145
                        && chunk.pageRange().endPageNumber() >= 145));
    }

    @Test
    void retrievesInfusionInstructionsFromWatermarkedPdf() throws Exception {
        var context = contextPipeline().prepare(content("1372805.pdf"),
                "Как подключить дополнительную магистраль для инфузии?");

        assertTrue(context.contextAvailable());
        assertTrue(context.selectedChunks().stream().anyMatch(chunk ->
                chunk.content().contains("дополнительную магистраль")
                        && chunk.pageRange().startPageNumber() <= 30
                        && chunk.pageRange().endPageNumber() >= 30));
    }

    @Test
    void chunksLargeDigitalInstructionWithoutOverflow() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1373967.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
                        && chunk.pageRange().startPageNumber() <= chunk.pageRange().endPageNumber()
                        && !chunk.content().isBlank()
        ));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.content().contains("Новорожденные")
        ));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .map(heading -> heading.text())
                        .toList().equals(List.of("Эксплуатация", "Усиленная подача кислорода"))
        ));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .map(heading -> heading.text())
                        .toList().equals(List.of("Техническое обслуживание", "Обзор"))
        ));
        assertTrue(document.chunks().stream().noneMatch(chunk -> {
            List<String> headings = chunk.sectionPath().headings().stream()
                    .map(heading -> heading.text())
                    .toList();
            return headings.size() > 1
                    && headings.get(0).equals(headings.get(1));
        }));
        assertTrue(document.chunks().stream().noneMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().startsWith("1 Извлечь предохранитель"))
        ));
        assertTrue(document.chunks().stream()
                .filter(chunk -> chunk.pageRange().startPageNumber() >= 200)
                .filter(chunk -> chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().equals("Ремонт")))
                .noneMatch(chunk -> chunk.content().contains("Через 6 лет")));
        assertTrue(document.chunks().stream().noneMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().contains("113Подтверждение"))
        ));
    }

    @Test
    void chunksCompactInstructionUsingCorruptedContentsPageNumbers() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("88888888.pdf"));

        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().equals("Работа с прибором"))
        ));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().equals("Работа от сети/батареек"))
        ));
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.sectionPath().headings().size() <= 1
                        && chunk.estimatedTokenCount() <= 1200
        ));
    }

    @Test
    void doesNotApplyPrintedContentsPagesToRomanNumberedFrontMatter() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1373589.pdf"));

        assertTrue(document.chunks().stream()
                .filter(chunk -> chunk.pageRange().startPageNumber() <= 4)
                .allMatch(chunk -> chunk.sectionPath().isRoot()));
    }

    @Test
    void chunksWatermarkedInstructionAfterExistingNoiseCleaning() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1372805.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
        ));
        assertTrue(document.chunks().stream().noneMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().matches(".*(?:34209|B\\. Braun|В\\. Braun).*"))
        ));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.content().contains("34209")
        ));
    }

    @Test
    void chunksScanLikeInstructionEvenWhenStructuredTablesAreAbsent() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1372722.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
        ));
        assertTrue(document.chunks().stream().noneMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().contains("дыхание.Переведите"))
        ));
    }

    @Test
    void chunksMultiPageErrorCodeTablesWithoutOverflow() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1373694.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
        ));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.content().contains("[TABLE")
        ));
        List<com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk> errorCodeParts =
                document.chunks().stream()
                        .filter(chunk -> chunk.content().contains("[TABLE PART"))
                        .filter(chunk -> chunk.content().contains("Error message on the screen"))
                        .toList();
        assertTrue(errorCodeParts.size() >= 2);
        assertTrue(errorCodeParts.stream().allMatch(chunk ->
                chunk.pageRange().startPageNumber() >= 145
                        && chunk.pageRange().endPageNumber() <= 148));
        assertTrue(errorCodeParts.stream().anyMatch(chunk ->
                chunk.pageRange().startPageNumber() > 145
                        || chunk.pageRange().endPageNumber() < 148));
        assertTrue(document.chunks().stream()
                .filter(chunk -> chunk.pageRange().startPageNumber() == 145)
                .anyMatch(chunk -> chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().equals(
                                "DESCRIBING CELL SAVER 5+ ERROR CODES"))));
        assertTrue(errorCodeParts.stream().noneMatch(chunk ->
                chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().contains("Mucous Membrane")
                                || heading.text().contains("Methylmethacrylate"))));
        assertTrue(document.chunks().stream()
                .filter(chunk -> chunk.pageRange().startPageNumber() >= 142)
                .noneMatch(chunk -> chunk.sectionPath().headings().stream()
                        .anyMatch(heading -> heading.text().equals("C. Methylmethacrylate"))));
    }

    @Test
    void chunksLargeFileWithinConfiguredBudget() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1373724.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
        ));
    }

    @Test
    void keepsOcrLogosAndFigureLabelsOutOfMultilingualSectionPaths() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1373228.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200));
        assertTrue(document.chunks().stream().noneMatch(chunk ->
                chunk.sectionPath().headings().stream().anyMatch(heading ->
                        heading.text().contains("SIXSilZ")
                                || heading.text().equals("QQQDI")
                                || heading.text().equals("POWER Cut"))));
        assertTrue(document.chunks().stream().anyMatch(chunk ->
                chunk.content().contains("POWER Cut")));
        assertTrue(document.chunks().stream().noneMatch(chunk ->
                chunk.pageRange().startPageNumber() >= 250
                        && chunk.sectionPath().headings().stream().anyMatch(heading ->
                                heading.text().equals("10.2 Графики мощности, напряжения и тока"))));
    }

    private PdfChunkingPipeline pipeline() {
        TextCleaner cleaner = new TextCleaner();
        PdfParsingPipeline parser = new PdfParsingPipeline(
                new TikaPdfReader(),
                new DocumentNoiseProfileBuilder(),
                cleaner,
                new PdfTableParsingService(
                        new TabulaPdfTableExtractor(),
                        new TableCandidateSelector(),
                        new TableNormalizer(cleaner),
                        new TableQualityFilter(),
                        new TableAssembler()
                ),
                new ParsedDocumentAssembler(new TextTableOverlapCleaner())
        );
        JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();
        TextSegmentSplitter textSplitter = new TextSegmentSplitter(new IcuTextBoundaryAdapter());
        TableTextSerializer tableSerializer = new TableTextSerializer();
        StructuredDocumentChunker chunker = new StructuredDocumentChunker(
                new ChunkingProperties(),
                tokenizer,
                new DocumentChunkSerializer(),
                tableSerializer,
                new TableChunkSplitter(
                        tableSerializer,
                        new TableHeaderDetector(),
                        textSplitter
                ),
                textSplitter
        );
        return new PdfChunkingPipeline(
                parser,
                new DocumentStructureAnalyzer(),
                chunker
        );
    }

    private PdfContextPreparationPipeline contextPipeline() {
        RetrievalProperties properties = new RetrievalProperties();
        JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();
        return new PdfContextPreparationPipeline(
                pipeline(),
                new LexicalChunkRanker(),
                new PdfContextAssembler(properties, tokenizer),
                tokenizer,
                properties
        );
    }

    private byte[] content(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getenv("PDF_INSTRUCTION_DIRECTORY"), name));
    }
}
