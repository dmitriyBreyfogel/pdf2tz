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
    }

    @Test
    void chunksWatermarkedInstructionAfterExistingNoiseCleaning() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1372805.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
        ));
    }

    @Test
    void chunksScanLikeInstructionEvenWhenStructuredTablesAreAbsent() throws Exception {
        ChunkedDocument document = pipeline().chunk(content("1372722.pdf"));

        assertFalse(document.chunks().isEmpty());
        assertTrue(document.chunks().stream().allMatch(chunk ->
                chunk.estimatedTokenCount() <= 1200
        ));
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
        TextSegmentSplitter textSplitter = new TextSegmentSplitter(
                new IcuTextBoundaryAdapter(),
                tokenizer
        );
        TableTextSerializer tableSerializer = new TableTextSerializer();
        StructuredDocumentChunker chunker = new StructuredDocumentChunker(
                new ChunkingProperties(),
                tokenizer,
                new DocumentChunkSerializer(),
                tableSerializer,
                new TableChunkSplitter(
                        tableSerializer,
                        new TableHeaderDetector(),
                        textSplitter,
                        tokenizer
                ),
                textSplitter
        );
        return new PdfChunkingPipeline(
                parser,
                new DocumentStructureAnalyzer(),
                chunker
        );
    }

    private byte[] content(String name) throws Exception {
        return Files.readAllBytes(Path.of(System.getenv("PDF_INSTRUCTION_DIRECTORY"), name));
    }
}
