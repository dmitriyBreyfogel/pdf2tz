package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;
import java.util.Objects;

/**
 * Результат поиска контекста для будущего LLM-вызова.
 * Если contextAvailable=false, совпадающих фрагментов не найдено и preparedPrompt пуст.
 */
public record PdfContextResponseDto(
        String prompt,
        boolean contextAvailable,
        String preparedPrompt,
        int estimatedTokenCount,
        int totalChunkCount,
        List<PdfDocumentChunkResponseDto> selectedChunks
) {
    public PdfContextResponseDto {
        prompt = Objects.requireNonNull(prompt, "Prompt must not be null");
        preparedPrompt = Objects.requireNonNull(preparedPrompt, "Prepared prompt must not be null");
        selectedChunks = List.copyOf(Objects.requireNonNull(selectedChunks, "Selected chunks must not be null"));
    }
}
