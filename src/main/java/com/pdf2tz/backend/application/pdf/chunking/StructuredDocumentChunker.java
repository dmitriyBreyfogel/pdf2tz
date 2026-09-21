package com.pdf2tz.backend.application.pdf.chunking;

import com.pdf2tz.backend.application.pdf.chunking.serialization.DocumentChunkSerializer;
import com.pdf2tz.backend.application.pdf.chunking.text.TextSegmentSplitter;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocumentBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredHeadingBlock;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredTextBlock;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Разбивает структурированный документ на token-bounded chunks без overlap.
 *
 * <p>На этой итерации реализованы heading events и текстовые блоки. Таблицы
 * подключаются следующим этапом отдельным splitter-ом, чтобы не смешивать
 * правила text fallback и table row preservation.</p>
 */
@Service
public class StructuredDocumentChunker {

    private final ChunkingProperties properties;
    private final LlmTokenizerPort tokenizer;
    private final DocumentChunkSerializer serializer;
    private final TextSegmentSplitter textSegmentSplitter;

    public StructuredDocumentChunker(
            ChunkingProperties properties,
            LlmTokenizerPort tokenizer,
            DocumentChunkSerializer serializer,
            TextSegmentSplitter textSegmentSplitter
    ) {
        this.properties = Objects.requireNonNull(properties, "Chunking properties must not be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer must not be null");
        this.serializer = Objects.requireNonNull(serializer, "Chunk serializer must not be null");
        this.textSegmentSplitter = Objects.requireNonNull(
                textSegmentSplitter,
                "Text segment splitter must not be null"
        );
    }

    /**
     * Разбивает документ в исходном порядке блоков.
     *
     * @param document структурированный документ
     * @return ordered chunks
     */
    public ChunkedDocument chunk(StructuredDocument document) {
        Objects.requireNonNull(document, "Structured document must not be null");

        List<DocumentChunk> chunks = new ArrayList<>();
        ChunkAccumulator accumulator = newAccumulator();
        SectionPath pendingHeadingPath = null;
        com.pdf2tz.backend.application.pdf.model.document.PageRange pendingHeadingRange = null;

        for (StructuredDocumentBlock block : document.blocks()) {
            if (block instanceof StructuredHeadingBlock headingBlock) {
                if (!accumulator.isEmpty()) {
                    chunks.add(accumulator.finish(chunks.size() + 1));
                    accumulator = newAccumulator();
                }

                if (pendingHeadingPath != null
                        && !isPrefix(pendingHeadingPath, headingBlock.sectionPath())) {
                    chunks.add(accumulator.finishHeadingOnly(
                            chunks.size() + 1,
                            pendingHeadingPath,
                            pendingHeadingRange
                    ));
                }
                pendingHeadingPath = headingBlock.sectionPath();
                pendingHeadingRange = headingBlock.pageRange();
                continue;
            }

            if (block instanceof StructuredTextBlock textBlock) {
                if (pendingHeadingPath != null) {
                    if (isPrefix(pendingHeadingPath, textBlock.sectionPath())) {
                        pendingHeadingPath = null;
                        pendingHeadingRange = null;
                    } else {
                        chunks.add(accumulator.finishHeadingOnly(
                                chunks.size() + 1,
                                pendingHeadingPath,
                                pendingHeadingRange
                        ));
                        pendingHeadingPath = null;
                        pendingHeadingRange = null;
                    }
                }
                accumulator = appendTextBlock(textBlock, accumulator, chunks);
                continue;
            }

            throw new IllegalArgumentException(
                    "Table blocks are not supported by the text-only chunking iteration: "
                            + block.getClass().getSimpleName()
            );
        }

        if (!accumulator.isEmpty()) {
            chunks.add(accumulator.finish(chunks.size() + 1));
        }
        if (pendingHeadingPath != null) {
            chunks.add(accumulator.finishHeadingOnly(
                    chunks.size() + 1,
                    pendingHeadingPath,
                    pendingHeadingRange
            ));
        }

        return new ChunkedDocument(chunks);
    }

    private ChunkAccumulator appendTextBlock(
            StructuredTextBlock textBlock,
            ChunkAccumulator accumulator,
            List<DocumentChunk> chunks
    ) {
        String remainder = textBlock.text();
        while (!remainder.isBlank()) {
            if (accumulator.isEmpty()) {
                if (accumulator.sectionPath() == null) {
                    // The path is initialized by the first successful add below.
                }
            } else if (!accumulator.sectionPath().equals(textBlock.sectionPath())) {
                chunks.add(accumulator.finish(chunks.size() + 1));
                accumulator = replaceAccumulator();
            }

            ChunkAccumulator current = accumulator;
            TextSegmentSplitterResult split = splitForAccumulator(
                    remainder,
                    textBlock,
                    current
            );
            if (split.accepted().isBlank()) {
                if (!current.isEmpty()) {
                    chunks.add(current.finish(chunks.size() + 1));
                    accumulator = replaceAccumulator();
                    continue;
                }
                throw new IllegalArgumentException("Text block cannot fit into chunk budget");
            }

            current.add(textBlock.sectionPath(), textBlock.pageRange(), split.accepted());
            remainder = split.remainder();
        }
        return accumulator;
    }

    private TextSegmentSplitterResult splitForAccumulator(
            String text,
            StructuredTextBlock textBlock,
            ChunkAccumulator accumulator
    ) {
        String remainder = text;
        var result = textSegmentSplitter.splitFirst(
                remainder,
                candidate -> canFit(accumulator, textBlock, candidate)
        );
        return new TextSegmentSplitterResult(
                result.acceptedPrefix(),
                result.remainder()
        );
    }

    private boolean canFit(
            ChunkAccumulator accumulator,
            StructuredTextBlock block,
            String candidate
    ) {
        if (accumulator.isEmpty()) {
            return accumulator.canFit(block.sectionPath(), candidate);
        }
        return accumulator.sectionPath().equals(block.sectionPath())
                && accumulator.canFit(candidate);
    }

    private ChunkAccumulator replaceAccumulator() {
        return newAccumulator();
    }

    private ChunkAccumulator newAccumulator() {
        return new ChunkAccumulator(serializer, tokenizer, properties.maxTokens());
    }

    private boolean isPrefix(SectionPath prefix, SectionPath path) {
        return prefix.commonPrefix(path).equals(prefix);
    }

    private record TextSegmentSplitterResult(
            String accepted,
            String remainder
    ) {
    }
}
