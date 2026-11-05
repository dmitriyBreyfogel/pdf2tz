package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;

import java.util.List;
import java.util.Set;

/** Finds chunks semantically related to medical-device maintenance in any language. */
public interface ChunkSemanticSearch {
    Set<Integer> findRelevantChunkNumbers(List<DocumentChunk> chunks);
}
