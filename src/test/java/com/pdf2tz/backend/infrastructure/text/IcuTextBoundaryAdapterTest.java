package com.pdf2tz.backend.infrastructure.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcuTextBoundaryAdapterTest {

    private final IcuTextBoundaryAdapter adapter = new IcuTextBoundaryAdapter();

    @Test
    void keepsOffsetsWithinOriginalTextAndIncludesEnd() {
        String text = "Т.е. изделие работает. CPAP mode включён!";

        List<Integer> sentenceOffsets = adapter.sentenceEndOffsets(text);
        List<Integer> wordOffsets = adapter.wordEndOffsets(text);

        assertEquals(text.length(), sentenceOffsets.get(sentenceOffsets.size() - 1));
        assertEquals(text.length(), wordOffsets.get(wordOffsets.size() - 1));
        assertTrue(sentenceOffsets.stream().allMatch(offset -> offset > 0 && offset <= text.length()));
        assertTrue(wordOffsets.stream().allMatch(offset -> offset > 0 && offset <= text.length()));
    }

    @Test
    void handlesMixedScriptsAndEmptyText() {
        String text = "Русский текст. English text. 中文。日本語。";

        assertTrue(adapter.sentenceEndOffsets(text).size() >= 3);
        assertEquals(List.of(), adapter.sentenceEndOffsets(""));
        assertEquals(List.of(), adapter.wordEndOffsets(""));
    }

    @Test
    void canBeUsedConcurrently() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<List<Integer>>> calls = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> (Callable<List<Integer>>) () ->
                            adapter.sentenceEndOffsets("Page " + index + ". Следующая страница."))
                    .toList();

            assertEquals(20, executor.invokeAll(calls).size());
        } finally {
            executor.shutdownNow();
        }
    }
}
