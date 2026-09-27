package com.pdf2tz.backend.application.pdf.model.structure;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;

import java.util.Objects;

/**
 * Структурное событие принятого заголовка.
 *
 * <p>Событие сохраняется отдельно от body-блоков, чтобы пустой или последний
 * раздел не исчезал из результата анализа.</p>
 *
 * @param sectionPath путь, включающий новый заголовок
 */
public record StructuredHeadingBlock(
        SectionPath sectionPath
) implements StructuredDocumentBlock {

    public StructuredHeadingBlock {
        sectionPath = Objects.requireNonNull(sectionPath, "Heading section path must not be null");
        if (sectionPath.isRoot()) {
            throw new IllegalArgumentException("Heading section path must not be root");
        }
    }

    @Override
    public PageRange pageRange() {
        SectionHeading heading = sectionPath.headings().get(sectionPath.headings().size() - 1);
        return PageRange.single(heading.pageNumber());
    }
}
