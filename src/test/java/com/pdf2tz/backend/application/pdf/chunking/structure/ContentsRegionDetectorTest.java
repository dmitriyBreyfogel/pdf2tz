package com.pdf2tz.backend.application.pdf.chunking.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentsRegionDetectorTest {

    private final ContentsRegionDetector detector = new ContentsRegionDetector();

    @Test
    void doesNotTreatNumberedDataRowsAsContents() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "Параметры изделия"),
                new SourceLine(1, 0, 1, 1, "Частота 10"),
                new SourceLine(1, 0, 2, 2, "Давление 20"),
                new SourceLine(1, 0, 3, 3, "Объем 30"),
                new SourceLine(1, 0, 4, 4, "Температура 40")
        );

        assertEquals(Set.of(), detector.analyze(lines).tocLineOrders());
    }

    @Test
    void recognizesContentsWithoutDotLeadersWhenItsTitleIsPresent() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "Содержание"),
                new SourceLine(1, 0, 1, 1, "Назначение 3"),
                new SourceLine(1, 0, 2, 2, "Применение 5"),
                new SourceLine(1, 0, 3, 3, "Эксплуатация 8"),
                new SourceLine(1, 0, 4, 4, "Обслуживание 12")
        );

        assertEquals(Set.of(1, 2, 3, 4), detector.analyze(lines).tocLineOrders());
    }
}
