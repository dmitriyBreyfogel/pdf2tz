package com.pdf2tz.backend.application.pdf.model.chunk;

import com.pdf2tz.backend.application.pdf.model.document.PageRange;
import com.pdf2tz.backend.application.pdf.model.structure.SectionPath;

import java.util.Objects;

/**
 * Самодостаточный bounded payload для последующей передачи в LLM.
 *
 * @param chunkNumber стабильный номер чанка внутри одного запроса
 * @param sectionPath полный структурный путь
 * @param pageRange диапазон страниц исходного документа
 * @param content сериализованное содержимое
 * @param estimatedTokenCount размер содержимого через выбранный tokenizer
 */
public record DocumentChunk(
        int chunkNumber,
        SectionPath sectionPath,
        PageRange pageRange,
        String content,
        int estimatedTokenCount
) {

    public DocumentChunk {
        if (chunkNumber < 1) {
            throw new IllegalArgumentException("Chunk number must be positive");
        }
        sectionPath = Objects.requireNonNull(sectionPath, "Chunk section path must not be null");
        pageRange = Objects.requireNonNull(pageRange, "Chunk page range must not be null");
        content = Objects.requireNonNull(content, "Chunk content must not be null").trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Chunk content must not be blank");
        }
        if (estimatedTokenCount < 1) {
            throw new IllegalArgumentException("Estimated token count must be positive");
        }
    }
}
