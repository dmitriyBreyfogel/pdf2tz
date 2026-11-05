package com.pdf2tz.backend.application.pdf.chunking;

import com.pdf2tz.backend.application.pdf.chunking.serialization.DocumentChunkSerializer;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mutable draft одного чанка внутри одного прохода chunker-а.
 */
final class ChunkAccumulator {

    private final DocumentChunkSerializer serializer;
    private final LlmTokenizerPort tokenizer;
    private final int maxTokens;
    private final List<String> bodyParts = new ArrayList<>();
    private SectionPath sectionPath;
    private PageRange pageRange;

    ChunkAccumulator(
            DocumentChunkSerializer serializer,
            LlmTokenizerPort tokenizer,
            int maxTokens
    ) {
        this.serializer = Objects.requireNonNull(serializer, "Chunk serializer must not be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer must not be null");
        if (maxTokens < 1) {
            throw new IllegalArgumentException("Maximum token count must be positive");
        }
        this.maxTokens = maxTokens;
    }

    boolean isEmpty() {
        return bodyParts.isEmpty();
    }

    SectionPath sectionPath() {
        return sectionPath;
    }

    boolean canFit(SectionPath path, String bodyPart) {
        Objects.requireNonNull(path, "Section path must not be null");
        Objects.requireNonNull(bodyPart, "Chunk body part must not be null");

        List<String> prospectiveParts = new ArrayList<>(bodyParts);
        prospectiveParts.add(bodyPart);
        return tokenizer.countTokens(serializer.serialize(path, prospectiveParts)) <= maxTokens;
    }

    boolean canFit(String bodyPart) {
        if (sectionPath == null) {
            throw new IllegalStateException("Chunk section path has not been initialized");
        }
        return canFit(sectionPath, bodyPart);
    }

    void add(SectionPath path, PageRange range, String bodyPart) {
        Objects.requireNonNull(path, "Chunk section path must not be null");
        Objects.requireNonNull(range, "Chunk page range must not be null");
        Objects.requireNonNull(bodyPart, "Chunk body part must not be null");
        if (bodyPart.isBlank()) {
            return;
        }
        if (sectionPath == null) {
            sectionPath = path;
            pageRange = range;
        } else {
            if (!sectionPath.equals(path)) {
                throw new IllegalArgumentException("Chunk cannot contain different section paths");
            }
            pageRange = pageRange.merge(range);
        }
        bodyParts.add(bodyPart);
    }

    DocumentChunk finish(int chunkNumber) {
        if (bodyParts.isEmpty()) {
            throw new IllegalStateException("Cannot finish an empty chunk");
        }
        String content = serializer.serialize(sectionPath, bodyParts);
        return new DocumentChunk(
                chunkNumber,
                sectionPath,
                pageRange,
                content,
                tokenizer.countTokens(content)
        );
    }

    DocumentChunk finishHeadingOnly(
            int chunkNumber,
            SectionPath headingPath,
            PageRange headingRange
    ) {
        String content = serializer.serialize(headingPath, List.of());
        int tokenCount = tokenizer.countTokens(content);
        if (tokenCount > maxTokens) {
            throw new IllegalArgumentException(
                    "Section prefix cannot fit into the configured chunk budget"
            );
        }
        return new DocumentChunk(
                chunkNumber,
                headingPath,
                headingRange,
                content,
                tokenCount
        );
    }
}
