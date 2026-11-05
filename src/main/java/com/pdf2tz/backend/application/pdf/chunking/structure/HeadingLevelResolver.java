package com.pdf2tz.backend.application.pdf.chunking.structure;

import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;

/**
 * Переводит принятого кандидата в уровень и доменную модель заголовка.
 */
final class HeadingLevelResolver {

    int resolve(HeadingCandidate candidate, ListContext context) {
        return switch (candidate.scheme()) {
            case DECIMAL -> candidate.numbering().size();
            case ROMAN, UNNUMBERED_UPPERCASE -> 1;
            case LETTER -> context.letterAsNested() ? 2 : 1;
            case TITLE_CASE -> context.titleCaseNested() ? 2 : 1;
        };
    }

    SectionHeading toHeading(HeadingCandidate candidate, ListContext context) {
        return new SectionHeading(
                resolve(candidate, context),
                candidate.inferredFromContents()
                        ? candidate.headingText()
                        : candidate.sourceLine().text().trim(),
                candidate.sourceLine().pageNumber()
        );
    }

    record ListContext(
            boolean letterAsNested,
            boolean titleCaseNested
    ) {
    }
}
