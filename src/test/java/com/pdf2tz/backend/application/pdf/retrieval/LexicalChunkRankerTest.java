package com.pdf2tz.backend.application.pdf.retrieval;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import com.pdf2tz.backend.error.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexicalChunkRankerTest {

    private final LexicalChunkRanker ranker = new LexicalChunkRanker();

    @Test
    void findsRussianInflectedMaintenanceTermsWithoutMatchingDifferentTopic() {
        List<DocumentChunk> chunks = List.of(
                chunk(1, "Проверка вентиляции и состояния пациента"),
                chunk(2, "Периодичности технического обслуживания указаны в таблице"),
                chunk(3, "Переход в режим ожидания")
        );

        var ranked = ranker.rank("периодичность технического обслуживания", chunks);

        assertEquals(2, ranked.get(0).chunk().chunkNumber());
        assertTrue(ranked.stream().noneMatch(result -> result.chunk().chunkNumber() == 3));
    }

    @Test
    void prefersSeveralMatchingTermsAndBreaksTiesBySourceOrder() {
        List<DocumentChunk> chunks = List.of(
                chunk(3, "Error code and description"),
                chunk(1, "Error code and description"),
                chunk(2, "Device description")
        );

        var ranked = ranker.rank("error code", chunks);

        assertEquals(List.of(1, 3), ranked.stream().limit(2)
                .map(result -> result.chunk().chunkNumber()).toList());
    }

    @Test
    void reportsNoMatchWithoutReturningUnrelatedChunks() {
        assertTrue(ranker.rank("стерилизация", List.of(chunk(1, "Порядок ремонта изделия"))).isEmpty());
        assertThrows(AppException.class, () -> ranker.rank("и в а", List.of(chunk(1, "Текст"))));
    }

    @Test
    void keepsCombiningMarksInsideWords() {
        var ranked = ranker.rank("परीक्षण", List.of(
                chunk(1, "उपकरण का परीक्षण"),
                chunk(2, "Техническое обслуживание")
        ));

        assertEquals(List.of(1), ranked.stream()
                .map(result -> result.chunk().chunkNumber()).toList());
    }

    @Test
    void rejoinsPdfLineWrapsForMatchingWithoutChangingSourceContent() {
        DocumentChunk wrapped = chunk(1, "Ответственный пер- сонал проводит осмотр");
        DocumentChunk hyphenated = chunk(2, "Другой пер-сонал");
        DocumentChunk lineWrapped = chunk(3, "Ответственный пер-\nсонал");
        DocumentChunk separateParagraph = chunk(4, "Нельзя пер-\n\nсонал");

        var ranked = ranker.rank("персонал", List.of(wrapped, hyphenated, lineWrapped, separateParagraph));

        assertEquals(List.of(1, 3), ranked.stream().map(result -> result.chunk().chunkNumber())
                .sorted().toList());
        assertEquals("Ответственный пер- сонал проводит осмотр", ranked.stream()
                .filter(result -> result.chunk().chunkNumber() == 1)
                .findFirst().orElseThrow().chunk().content());
    }

    @Test
    void ranksHeadingOnlyChunksAfterContentButKeepsThemAsFallback() {
        SectionPath path = new SectionPath(List.of(new SectionHeading(1, "Техническое обслуживание", 2)));
        DocumentChunk heading = new DocumentChunk(1, path, PageRange.single(2),
                "# Техническое обслуживание", 6);
        DocumentChunk content = new DocumentChunk(2, path, PageRange.single(3),
                "# Техническое обслуживание\n\nОсмотр выполнять каждые 12 месяцев", 25);

        var ranked = ranker.rank("техническое обслуживание", List.of(heading, content));

        assertEquals(List.of(2, 1), ranked.stream().map(result -> result.chunk().chunkNumber()).toList());
        assertEquals(1, ranker.rank("техническое обслуживание", List.of(heading))
                .get(0).chunk().chunkNumber());
    }

    private DocumentChunk chunk(int number, String text) {
        return new DocumentChunk(number, SectionPath.root(), PageRange.single(number), text, 20);
    }
}
