package com.pdf2tz.backend.application.pdf.chunking.serialization;

import com.pdf2tz.backend.application.pdf.model.structure.SectionHeading;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Собирает конечный content чанка из section prefix и ordered body parts.
 */
@Component
public class DocumentChunkSerializer {

    /**
     * Сериализует body под заданным структурным путём.
     *
     * @param sectionPath путь раздела
     * @param bodyParts ordered text/table payloads
     * @return exact chunk content
     */
    public String serialize(SectionPath sectionPath, List<String> bodyParts) {
        Objects.requireNonNull(sectionPath, "Section path must not be null");
        Objects.requireNonNull(bodyParts, "Chunk body parts must not be null");

        String body = bodyParts.stream()
                .map(part -> Objects.requireNonNull(part, "Chunk body part must not be null").trim())
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining("\n\n"));
        String prefix = serializeSectionPath(sectionPath);

        if (prefix.isBlank()) {
            return body;
        }
        if (body.isBlank()) {
            return prefix;
        }
        return prefix + "\n\n" + body;
    }

    /**
     * Сериализует только section prefix. Уровни выше шести сохраняются в
     * metadata пути, а визуально используют максимально глубокий Markdown level.
     *
     * @param sectionPath путь раздела
     * @return heading prefix либо пустая строка для root
     */
    public String serializeSectionPath(SectionPath sectionPath) {
        Objects.requireNonNull(sectionPath, "Section path must not be null");
        return sectionPath.headings().stream()
                .map(this::serializeHeading)
                .collect(Collectors.joining("\n"));
    }

    private String serializeHeading(SectionHeading heading) {
        int markdownLevel = Math.min(heading.level(), 6);
        return "#".repeat(markdownLevel) + " " + heading.text();
    }
}
