package com.pdf2tz.backend.application.pdf.model.retrieval;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;

import java.util.List;
import java.util.Objects;

/**
 * Результат поиска по одной инструкции: текст для будущего LLM-вызова и его источники.
 * Пустой preparedPrompt означает, что по запросу не найдено текстовых совпадений.
 *
 * @param prompt исходный запрос пользователя после удаления краевых пробелов
 * @param preparedPrompt запрос с отобранным контекстом либо пустая строка
 * @param estimatedTokenCount число токенов в preparedPrompt
 * @param totalChunkCount число чанков, просмотренных поиском
 * @param selectedChunks выбранные чанки в порядке исходного документа
 */
public record PreparedPdfContext(
        String prompt,
        String preparedPrompt,
        int estimatedTokenCount,
        int totalChunkCount,
        List<DocumentChunk> selectedChunks
) {
    public PreparedPdfContext {
        prompt = Objects.requireNonNull(prompt, "Prompt must not be null");
        preparedPrompt = Objects.requireNonNull(preparedPrompt, "Prepared prompt must not be null");
        selectedChunks = List.copyOf(Objects.requireNonNull(selectedChunks, "Selected chunks must not be null"));
        if (estimatedTokenCount < 0 || totalChunkCount < 0) {
            throw new IllegalArgumentException("Counts must not be negative");
        }
        if (preparedPrompt.isBlank() != selectedChunks.isEmpty()) {
            throw new IllegalArgumentException("Prepared prompt and selected chunks must be present together");
        }
    }

    public boolean contextAvailable() {
        return !selectedChunks.isEmpty();
    }
}
