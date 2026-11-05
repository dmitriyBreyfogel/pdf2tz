package com.pdf2tz.backend.application.maintenance;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Selects maintenance evidence from structural headings and document content. */
@Component
public class MaintenanceChunkSelector {
    private final ChunkSemanticSearch semanticSearch;
    private static final List<String> MAINTENANCE = List.of("профилактическ", "техническое обслуживание", "техобслужив", "очистк", "дезинфек", "обработк", "maintenance", "cleaning", "disinfection", "preventive maintenance", "service intervals", "wartung", "reinig", "desinfiz", "sterilisation");
    private static final List<String> CONSUMABLES = List.of("расходн", "принадлежност", "аксессуар", "consumable", "supplies", "verbrauchsmaterial");
    private static final List<String> NEGATIVE = List.of("содержание", "безопасност", "предупрежден", "эксплуатац", "установк", "описание", "safety", "safe handling", "s a fe", "operating");
    private static final List<String> SERVICE_ACTIONS = List.of("очист", "очищ", "дезинф", "стерилиз", "обработ", "провер", "замен", "смаз", "калибр", "inspect", "clean", "disinfect", "steriliz", "replace", "calibrat", "reinig", "desinfiz");
    private static final List<String> CARE_ACTIONS = List.of("очист", "очищ", "дезинф", "стерилиз", "обработ", "clean", "disinfect", "steriliz", "reinig", "desinfiz");
    private static final List<String> DIRECT_MAINTENANCE = List.of("техническое обслуживание", "техобслужив", "preventive maintenance", "technical maintenance", "service interval");
    private static final List<String> NEGATED_MAINTENANCE = List.of("не требует технического обслуживания", "no maintenance", "maintenance-free");

    public MaintenanceChunkSelector(ChunkSemanticSearch semanticSearch) {
        this.semanticSearch = semanticSearch;
    }

    public List<DocumentChunk> select(List<DocumentChunk> chunks) {
        var relevantNumbers = new LinkedHashSet<Integer>(semanticSearch.findRelevantChunkNumbers(chunks));
        relevantNumbers.removeIf(number -> chunks.stream().filter(chunk -> chunk.chunkNumber() == number)
                .anyMatch(this::excluded));
        List<DocumentChunk> ranked = chunks.stream().filter(c -> score(c) > 0)
                .sorted(Comparator.comparingInt(this::score).reversed().thenComparingInt(DocumentChunk::chunkNumber)).toList();
        var numbers = new LinkedHashSet<Integer>(relevantNumbers);
        if (ranked.isEmpty() && numbers.isEmpty()) return chunks.stream().limit(1).toList();
        ranked.forEach(chunk -> numbers.add(chunk.chunkNumber()));
        expandMaintenanceSections(chunks, numbers);
        if (!chunks.isEmpty() && !isContents(chunks.get(0))) numbers.add(1); // паспортные данные изделия
        return chunks.stream().filter(c -> numbers.contains(c.chunkNumber())).toList();
    }

    /**
     * A work table often starts in one chunk and continues in child sections whose headings do not
     * themselves repeat "maintenance". Retaining the complete positive section avoids cutting off
     * frequencies, exceptions and consumables at a chunk boundary.
     */
    private void expandMaintenanceSections(List<DocumentChunk> chunks, LinkedHashSet<Integer> numbers) {
        List<List<SectionHeading>> scopes = chunks.stream()
                .filter(chunk -> numbers.contains(chunk.chunkNumber()))
                .map(this::maintenanceScope)
                .filter(scope -> !scope.isEmpty())
                .distinct()
                .toList();
        chunks.stream().filter(chunk -> scopes.stream().anyMatch(scope -> belongsToScope(chunk, scope)))
                .filter(chunk -> !excluded(chunk)).map(DocumentChunk::chunkNumber).forEach(numbers::add);
    }

    private List<SectionHeading> maintenanceScope(DocumentChunk chunk) {
        List<SectionHeading> path = chunk.sectionPath().headings();
        for (int index = path.size() - 1; index >= 0; index--) {
            String heading = path.get(index).text().toLowerCase(Locale.ROOT);
            if (matches(heading, MAINTENANCE) + matches(heading, CONSUMABLES) > 0) {
                return path.subList(0, index + 1);
            }
        }
        return List.of();
    }

    private boolean belongsToScope(DocumentChunk chunk, List<SectionHeading> scope) {
        List<SectionHeading> headings = chunk.sectionPath().headings();
        return headings.size() >= scope.size() && headings.subList(0, scope.size()).equals(scope);
    }
    private int score(DocumentChunk chunk) {
        String headings = headings(chunk), content = chunk.content().toLowerCase(Locale.ROOT);
        if (excluded(chunk)) return -1000;
        int consumableHeadings = matches(headings, CONSUMABLES);
        int headingSignals = matches(headings, MAINTENANCE) + consumableHeadings;

        int score = matches(headings, MAINTENANCE) * 100 + matches(headings, CONSUMABLES) * 70;
        boolean directMaintenance = any(content, DIRECT_MAINTENANCE);
        boolean serviceAction = any(content, SERVICE_ACTIONS);
        boolean careAction = any(content, CARE_ACTIONS);
        boolean frequency = containsFrequency(content);
        boolean table = content.contains("[table");
        boolean strongContent = directMaintenance && serviceAction
                || careAction && (headingSignals > 0 || frequency || table);
        if (headingSignals == 0 && !strongContent) return 0;
        if (directMaintenance && serviceAction) score += 45;
        if (careAction && (headingSignals > 0 || frequency || table)) score += 35;
        return score;
    }
    private int matches(String text, List<String> terms) { return (int) terms.stream().filter(text::contains).count(); }
    private boolean any(String text, List<String> terms) { return terms.stream().anyMatch(text::contains); }
    private boolean containsFrequency(String content) {
        return content.matches("(?s).*\\b(?:ежеднев\\p{L}*|еженедел\\p{L}*|ежемесяч\\p{L}*|ежегод\\p{L}*|кажд\\p{L}*|(?:час|месяц|год)\\p{L}*|weekly|monthly|annually|every)\\b.*");
    }
    private boolean isContents(DocumentChunk chunk) {
        String headings = headings(chunk);
        String content = chunk.content().toLowerCase(Locale.ROOT);
        return content.contains("содержание") || headings.contains("содержание") || content.matches("(?s).*(?:\\.\\s*){6,}.*");
    }
    private boolean excluded(DocumentChunk chunk) {
        return isContents(chunk) || excludedByHeadingContext(chunk)
                || any(chunk.content().toLowerCase(Locale.ROOT), NEGATED_MAINTENANCE);
    }
    private boolean excludedByHeadingContext(DocumentChunk chunk) {
        List<String> headings = chunk.sectionPath().headings().stream()
                .map(heading -> heading.text().toLowerCase(Locale.ROOT)).toList();
        int lastNegative = -1;
        for (int index = 0; index < headings.size(); index++) {
            if (any(headings.get(index), NEGATIVE)) lastNegative = index;
        }
        return lastNegative >= 0 && headings.subList(lastNegative + 1, headings.size()).stream()
                .noneMatch(heading -> matches(heading, MAINTENANCE) > 0);
    }
    private String headings(DocumentChunk chunk) { return chunk.sectionPath().headings().stream().map(h -> h.text().toLowerCase(Locale.ROOT)).reduce("", (a, b) -> a + " " + b); }
}
