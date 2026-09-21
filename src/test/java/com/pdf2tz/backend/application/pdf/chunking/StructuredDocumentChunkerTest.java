package com.pdf2tz.backend.application.pdf.chunking;

import com.pdf2tz.backend.application.pdf.chunking.serialization.DocumentChunkSerializer;
import com.pdf2tz.backend.application.pdf.chunking.text.TextSegmentSplitter;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredHeadingBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredTextBlock;
import com.pdf2tz.backend.infrastructure.text.IcuTextBoundaryAdapter;
import com.pdf2tz.backend.infrastructure.text.JTokkitTokenizerAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredDocumentChunkerTest {

    @Test
    void combinesSmallTextAndKeepsSectionPath() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxTokens(128);
        StructuredDocumentChunker chunker = chunker(properties);
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "1 Operation", 2)));

        ChunkedDocument result = chunker.chunk(new StructuredDocument(List.of(
                new StructuredHeadingBlock(path),
                new StructuredTextBlock(path, new com.pdf2tz.backend.application.pdf.model.document.PageRange(2, 2),
                        "First sentence. Second sentence.")
        )));

        assertEquals(1, result.chunks().size());
        assertEquals(path, result.chunks().get(0).sectionPath());
        assertTrue(result.chunks().get(0).content().contains("# 1 Operation"));
    }

    @Test
    void splitsLargeTextWithoutExceedingConfiguredLimit() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxTokens(128);
        JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();
        StructuredDocumentChunker chunker = chunker(properties);
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "1 Operation", 1)));
        String text = "Длинный фрагмент инструкции. ".repeat(180);

        ChunkedDocument result = chunker.chunk(new StructuredDocument(List.of(
                new StructuredHeadingBlock(path),
                new StructuredTextBlock(path,
                        com.pdf2tz.backend.application.pdf.model.document.PageRange.single(1),
                        text)
        )));

        assertTrue(result.chunks().size() > 1);
        assertTrue(result.chunks().stream()
                .allMatch(chunk -> chunk.estimatedTokenCount() <= properties.maxTokens()));
        assertEquals(
                java.util.stream.IntStream.rangeClosed(1, result.chunks().size()).boxed().toList(),
                result.chunks().stream().map(chunk -> chunk.chunkNumber()).toList()
        );
        assertTrue(result.chunks().stream().allMatch(chunk ->
                tokenizer.countTokens(chunk.content()) <= properties.maxTokens()
        ));
    }

    @Test
    void emitsHeadingOnlyChunkForEmptyTrailingSection() {
        ChunkingProperties properties = new ChunkingProperties();
        StructuredDocumentChunker chunker = chunker(properties);
        SectionPath first = new SectionPath(List.of(new SectionHeading(1, "1 Main", 1)));
        SectionPath second = new SectionPath(List.of(new SectionHeading(1, "2 Appendix", 2)));

        ChunkedDocument result = chunker.chunk(new StructuredDocument(List.of(
                new StructuredHeadingBlock(first),
                new StructuredTextBlock(first,
                        com.pdf2tz.backend.application.pdf.model.document.PageRange.single(1),
                        "Body"),
                new StructuredHeadingBlock(second)
        )));

        assertEquals(2, result.chunks().size());
        assertEquals(second, result.chunks().get(1).sectionPath());
        assertTrue(result.chunks().get(1).content().contains("2 Appendix"));
    }

    private StructuredDocumentChunker chunker(ChunkingProperties properties) {
        JTokkitTokenizerAdapter tokenizer = new JTokkitTokenizerAdapter();
        DocumentChunkSerializer serializer = new DocumentChunkSerializer();
        return new StructuredDocumentChunker(
                properties,
                tokenizer,
                serializer,
                new TextSegmentSplitter(new IcuTextBoundaryAdapter(), tokenizer)
        );
    }
}
