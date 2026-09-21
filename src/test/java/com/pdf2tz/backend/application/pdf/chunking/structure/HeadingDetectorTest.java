package com.pdf2tz.backend.application.pdf.chunking.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadingDetectorTest {

    private final HeadingDetector detector = new HeadingDetector();

    @Test
    void acceptsStableDecimalHierarchyAndRejectsMeasurementAndInstructions() {
        List<SourceLine> lines = lines(
                "1 НАЗНАЧЕНИЕ",
                "Описание изделия.",
                "1.1 Область применения",
                "Текст раздела.",
                "1.5 mg/ml",
                "1) нажмите кнопку включения.",
                "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"
        );

        List<HeadingCandidate> headings = detector.detect(lines);

        assertEquals(
                List.of("1 НАЗНАЧЕНИЕ", "1.1 Область применения", "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"),
                headings.stream().map(candidate -> candidate.sourceLine().text()).toList()
        );
    }

    @Test
    void acceptsRepeatedUppercaseSections() {
        List<HeadingCandidate> headings = detector.detect(lines(
                "ЭКСПЛУАТАЦИЯ",
                "Подготовьте изделие.",
                "ТЕХНИЧЕСКОЕ ОБСЛУЖИВАНИЕ",
                "Очистите изделие."
        ));

        assertEquals(2, headings.size());
        assertTrue(headings.stream().allMatch(candidate ->
                candidate.scheme() == HeadingScheme.UNNUMBERED_UPPERCASE
        ));
    }

    @Test
    void ignoresContentsEntriesAndCaptions() {
        List<HeadingCandidate> headings = detector.detect(lines(
                "СОДЕРЖАНИЕ",
                "1 НАЗНАЧЕНИЕ ........ 3",
                "1.1 ОБЛАСТЬ ПРИМЕНЕНИЯ .... 4",
                "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ .... 8",
                "3 ЭКСПЛУАТАЦИЯ .... 12",
                "Рисунок 1. Общий вид изделия",
                "Таблица 2. Основные характеристики"
        ));

        assertTrue(headings.isEmpty());
    }

    private List<SourceLine> lines(String... values) {
        return java.util.stream.IntStream.range(0, values.length)
                .mapToObj(index -> new SourceLine(1, 0, index, index, values[index]))
                .toList();
    }
}
