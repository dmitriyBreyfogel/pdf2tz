package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
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
                .filter(chunk -> score(chunk.content()) > 1).limit(12).toList();
        if (relevant.isEmpty()) return chunks.stream().limit(18).toList();
        var numbers = new LinkedHashSet<Integer>();
        relevant.forEach(chunk -> {
            numbers.add(chunk.chunkNumber());
            numbers.add(chunk.chunkNumber() - 1);
            numbers.add(chunk.chunkNumber() + 1);
        });
        // Первый chunk сохраняет паспортные данные изделия, но не влияет на ранжирование регламентов.
        numbers.add(1);
        return chunks.stream().filter(chunk -> numbers.contains(chunk.chunkNumber())).limit(24).toList();
    }

    private int score(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("содержание") || lower.matches("(?s).*\\.{8,}.*")) return -100;
        int matches = (int) MARKERS.stream().filter(lower::contains).count();
        if (lower.contains("техническое обслуживание")) matches += 8;
        if (lower.contains("периодичность технического")) matches += 8;
        if (lower.contains("расходные материалы")) matches += 6;
        return matches;
    }
}
