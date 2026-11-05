package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceChunkSelectorTest {
    @Test
    void retainsSemanticCandidateWhenNoLanguageSpecificRuleMatches() {
        MaintenanceChunkSelector selector = new MaintenanceChunkSelector(chunks -> Set.of(2));
        List<DocumentChunk> selected = selector.select(List.of(
                chunk(1, "Device manual"),
                chunk(2, "任意语言的维护说明")
        ));

        assertEquals(List.of(1, 2), selected.stream().map(DocumentChunk::chunkNumber).toList());
    }

    @Test
    void rejectsSemanticCandidateFromSafetySection() {
        MaintenanceChunkSelector selector = new MaintenanceChunkSelector(chunks -> Set.of(2));
        DocumentChunk safety = new DocumentChunk(2, new SectionPath(List.of(new com.pdf2tz.backend.application.pdf.model.structure.SectionHeading(1, "Safety instructions", 2))), PageRange.single(2), "Inspect before use", 10);

        assertEquals(List.of(1), selector.select(List.of(chunk(1, "Device manual"), safety)).stream().map(DocumentChunk::chunkNumber).toList());
    }

    private DocumentChunk chunk(int number, String content) {
        return new DocumentChunk(number, SectionPath.root(), PageRange.single(number), content, 10);
    }
}
