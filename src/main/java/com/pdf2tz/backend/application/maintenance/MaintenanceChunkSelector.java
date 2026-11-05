package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Selects maintenance evidence from structural headings and document content. */
@Component
public class MaintenanceChunkSelector {
    private static final List<String> MAINTENANCE = List.of("профилактическ", "техническое обслуживание", "техобслужив", "очистк", "дезинфек", "обработк", "maintenance", "cleaning", "disinfection", "preventive maintenance", "service intervals");
    private static final List<String> CONSUMABLES = List.of("расходн", "принадлежност", "аксессуар", "consumable", "supplies");
    private static final List<String> NEGATIVE = List.of("содержание", "безопасност", "предупрежден", "эксплуатац", "установк", "описание");
    private static final List<String> SERVICE_ACTIONS = List.of("очист", "дезинф", "стерилиз", "обработ", "провер", "замен", "смаз", "калибр", "inspect", "clean", "disinfect", "steriliz", "replace", "calibrat");
    private static final List<String> CARE_ACTIONS = List.of("очист", "дезинф", "стерилиз", "обработ", "clean", "disinfect", "steriliz");
    private static final List<String> DIRECT_MAINTENANCE = List.of("техническое обслуживание", "техобслужив", "preventive maintenance", "technical maintenance", "service interval");

    public List<DocumentChunk> select(List<DocumentChunk> chunks) {
        List<DocumentChunk> ranked = chunks.stream().filter(c -> score(c) > 0)
                .sorted(Comparator.comparingInt(this::score).reversed().thenComparingInt(DocumentChunk::chunkNumber)).limit(12).toList();
        if (ranked.isEmpty()) return chunks.stream().limit(1).toList();
        var numbers = new LinkedHashSet<Integer>();
        ranked.forEach(chunk -> {
            numbers.add(chunk.chunkNumber());
            addNeighbour(chunks, numbers, chunk, chunk.chunkNumber() - 1);
            addNeighbour(chunks, numbers, chunk, chunk.chunkNumber() + 1);
        });
        numbers.add(1); // паспортные данные изделия, не участвует в ранжировании
        return chunks.stream().filter(c -> numbers.contains(c.chunkNumber())).limit(24).toList();
    }

    private void addNeighbour(List<DocumentChunk> chunks, LinkedHashSet<Integer> numbers, DocumentChunk source, int number) {
        if (number < 1 || number > chunks.size()) return;
        DocumentChunk neighbour = chunks.get(number - 1);
        if (maintenanceHeading(neighbour) && headings(source).equals(headings(neighbour))) numbers.add(number);
    }
    private int score(DocumentChunk chunk) {
        String headings = headings(chunk), content = chunk.content().toLowerCase(Locale.ROOT);
        if (content.contains("содержание") || headings.contains("содержание") || content.matches("(?s).*\\.{8,}.*")) return -1000;
        int headingSignals = matches(headings, MAINTENANCE) + matches(headings, CONSUMABLES);
        if (headingSignals == 0 && any(headings, NEGATIVE)) return -1000;

        int score = matches(headings, MAINTENANCE) * 100 + matches(headings, CONSUMABLES) * 70;
        if (any(content, DIRECT_MAINTENANCE) && any(content, SERVICE_ACTIONS)) score += 45;
        if (any(content, CARE_ACTIONS)) score += 35;
        if (any(content, CONSUMABLES) && any(content, SERVICE_ACTIONS)) score += 25;
        if (headingSignals == 0 && score < 35) return 0;
        return score;
    }
    private boolean maintenanceHeading(DocumentChunk chunk) { return matches(headings(chunk), MAINTENANCE) + matches(headings(chunk), CONSUMABLES) > 0; }
    private int matches(String text, List<String> terms) { return (int) terms.stream().filter(text::contains).count(); }
    private boolean any(String text, List<String> terms) { return terms.stream().anyMatch(text::contains); }
    private String headings(DocumentChunk chunk) { return chunk.sectionPath().headings().stream().map(h -> h.text().toLowerCase(Locale.ROOT)).reduce("", (a, b) -> a + " " + b); }
}
