package com.pdf2tz.backend.application.pdf.chunking.structure;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepeatedPageHeaderDetectorTest {

    @Test
    void recognizesOcrVariantsOfRepeatedHeaderButKeepsDifferentSectionTitles() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "ST0RZKARL STORZ ENDOSKO"),
                new SourceLine(1, 0, 1, 1, "ТЕХНИЧЕСКИЕ ДАННЫЕ"),
                new SourceLine(2, 0, 0, 2, "SIORZKARL STORZ ENDOSKO"),
                new SourceLine(2, 0, 1, 3, "ЭКСПЛУАТАЦИЯ"),
                new SourceLine(3, 0, 0, 4, "STORZKARL STORZ ENDOSKO"),
                new SourceLine(3, 0, 1, 5, "ОБСЛУЖИВАНИЕ"),
                new SourceLine(4, 0, 0, 6, "KARL SIXSILZ ENDOSKOK"),
                new SourceLine(4, 0, 1, 7, "РАЗДЕЛ ЧЕТЫРЕ")
        );

        assertEquals(Set.of(0, 2, 4), new RepeatedPageHeaderDetector().detect(lines));
        assertEquals(List.of("ТЕХНИЧЕСКИЕ ДАННЫЕ", "ЭКСПЛУАТАЦИЯ", "ОБСЛУЖИВАНИЕ",
                        "РАЗДЕЛ ЧЕТЫРЕ"),
                new HeadingDetector().detect(lines).stream()
                        .map(candidate -> candidate.sourceLine().text())
                        .toList());
    }
}
