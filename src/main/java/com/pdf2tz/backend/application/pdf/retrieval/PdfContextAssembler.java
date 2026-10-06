package com.pdf2tz.backend.application.pdf.retrieval;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.application.pdf.model.retrieval.PreparedPdfContext;
import com.pdf2tz.backend.application.pdf.retrieval.LexicalChunkRanker.RankedChunk;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Отбирает найденные фрагменты под общий бюджет и готовит текст с указанием источников.
 * Выбор идёт по релевантности, а в готовом запросе чанки стоят в порядке PDF.
 */
@Component
public class PdfContextAssembler {

    private final RetrievalProperties properties;
    private final LlmTokenizerPort tokenizer;

    public PdfContextAssembler(RetrievalProperties properties, LlmTokenizerPort tokenizer) {
        this.properties = Objects.requireNonNull(properties, "Retrieval properties must not be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer must not be null");
    }

    /**
     * @param prompt проверенный запрос пользователя
     * @param ranked чанки в порядке релевантности
     * @param totalChunkCount число просмотренных чанков
     * @return контекст и ссылки на выбранные источники
     */
    public PreparedPdfContext assemble(String prompt, List<RankedChunk> ranked, int totalChunkCount) {
        Objects.requireNonNull(prompt, "Prompt must not be null");
        Objects.requireNonNull(ranked, "Ranked chunks must not be null");
        if (ranked.isEmpty()) {
            return new PreparedPdfContext(prompt, "", 0, totalChunkCount, List.of());
        }

        List<DocumentChunk> selected = new ArrayList<>();
        String preparedPrompt = "";
        for (RankedChunk candidate : ranked) {
            if (selected.size() == properties.maxChunks()) {
                break;
            }
            List<DocumentChunk> proposed = new ArrayList<>(selected);
            proposed.add(candidate.chunk());
            proposed.sort(Comparator.comparingInt(DocumentChunk::chunkNumber));
            String proposedPrompt = serialize(prompt, proposed);
            if (tokenizer.countTokens(proposedPrompt) <= properties.maxContextTokens()) {
                selected = proposed;
                preparedPrompt = proposedPrompt;
            }
        }

        if (selected.isEmpty()) {
            throw AppException.build(ErrorCode.CONTEXT_BUDGET_EXCEEDED,
                    "Ни один подходящий фрагмент не помещается в бюджет контекста");
        }
        return new PreparedPdfContext(prompt, preparedPrompt,
                tokenizer.countTokens(preparedPrompt), totalChunkCount, selected);
    }

    private String serialize(String prompt, List<DocumentChunk> chunks) {
        String sources = chunks.stream()
                .map(chunk -> "[SOURCE chunk=" + chunk.chunkNumber()
                        + " pages=" + chunk.pageRange().startPageNumber()
                        + "-" + chunk.pageRange().endPageNumber() + "]\n"
                        + chunk.content() + "\n[/SOURCE]")
                .collect(Collectors.joining("\n\n"));
        return "Запрос пользователя:\n" + prompt
                + "\n\nФрагменты эксплуатационной документации с исходными страницами:\n\n"
                + sources;
    }
}
