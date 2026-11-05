package com.pdf2tz.backend.application.pdf.model.structure;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Неизменяемый путь от корня документа до текущего раздела.
 *
 * @param headings заголовки пути в порядке от верхнего уровня к текущему
 */
public record SectionPath(
        List<SectionHeading> headings
) {

    public SectionPath {
        headings = List.copyOf(Objects.requireNonNull(headings, "Section path headings must not be null"));
        validateLevels(headings);
    }

    public static SectionPath root() {
        return new SectionPath(List.of());
    }

    public boolean isRoot() {
        return headings.isEmpty();
    }

    public SectionPath commonPrefix(SectionPath other) {
        Objects.requireNonNull(other, "Other section path must not be null");
        int prefixLength = (int) IntStream.range(
                        0,
                        Math.min(headings.size(), other.headings.size())
                )
                .takeWhile(index -> headings.get(index).equals(other.headings.get(index)))
                .count();
        return new SectionPath(headings.subList(0, prefixLength));
    }

    private void validateLevels(List<SectionHeading> headings) {
        for (int index = 1; index < headings.size(); index++) {
            if (headings.get(index - 1).level() >= headings.get(index).level()) {
                throw new IllegalArgumentException("Section path levels must be strictly increasing");
            }
        }
    }
}
