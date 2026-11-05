package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Deterministic pre-filter: reduces noise but never replaces LLM source validation. */
@Component
public class MaintenanceChunkSelector {
    private static final List<String> MARKERS = List.of(
            "техническ", "обслужив", "периодич", "очист", "дезинф", "провер", "замен",
            "расходн", "салфет", "спирт", "maintenance", "clean", "inspection", "service", "replace"
    );

    public List<DocumentChunk> select(List<DocumentChunk> chunks) {
        List<DocumentChunk> relevant = chunks.stream()
                .sorted(Comparator.<DocumentChunk>comparingInt(chunk -> score(chunk.content())).reversed()
                        .thenComparingInt(DocumentChunk::chunkNumber))
                .filter(chunk -> score(chunk.content()) > 0).limit(18)
                .sorted(Comparator.comparingInt(DocumentChunk::chunkNumber)).toList();
        return relevant.isEmpty() ? chunks.stream().limit(18).toList() : relevant;
    }

    private int score(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return (int) MARKERS.stream().filter(lower::contains).count();
    }
}
