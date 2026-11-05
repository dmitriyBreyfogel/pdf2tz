package com.pdf2tz.backend.infrastructure.embedding;

import com.pdf2tz.backend.application.maintenance.ChunkSemanticSearch;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "pdf2tz.embeddings", name = "enabled", havingValue = "false", matchIfMissing = true)
class DisabledChunkSemanticSearch implements ChunkSemanticSearch {
    @Override
    public Set<Integer> findRelevantChunkNumbers(List<DocumentChunk> chunks) {
        return Set.of();
    }
}
