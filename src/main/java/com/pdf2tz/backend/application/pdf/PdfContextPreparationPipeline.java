package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.retrieval.PreparedPdfContext;
import com.pdf2tz.backend.application.pdf.retrieval.LexicalChunkRanker;
import com.pdf2tz.backend.application.pdf.retrieval.PdfContextAssembler;
import com.pdf2tz.backend.application.pdf.retrieval.RetrievalProperties;
import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Готовит проверяемый контекст по PDF и запросу пользователя для будущего вызова LLM.
 * Использует существующий chunking pipeline; PDF не парсится повторно другим путём.
 */
@Service
public class PdfContextPreparationPipeline {

    private final PdfChunkingPipeline chunkingPipeline;
    private final LexicalChunkRanker ranker;
    private final PdfContextAssembler assembler;
    private final LlmTokenizerPort tokenizer;
    private final RetrievalProperties properties;

    public PdfContextPreparationPipeline(PdfChunkingPipeline chunkingPipeline,
                                         LexicalChunkRanker ranker,
                                         PdfContextAssembler assembler,
                                         LlmTokenizerPort tokenizer,
                                         RetrievalProperties properties) {
        this.chunkingPipeline = Objects.requireNonNull(chunkingPipeline, "Chunking pipeline must not be null");
        this.ranker = Objects.requireNonNull(ranker, "Chunk ranker must not be null");
        this.assembler = Objects.requireNonNull(assembler, "Context assembler must not be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer must not be null");
        this.properties = Objects.requireNonNull(properties, "Retrieval properties must not be null");
    }

    /**
     * @param pdfContent исходный PDF
     * @param userPrompt требования пользователя к регламенту или поисковый вопрос
     * @return отобранные чанки и текст с маркерами страниц; вызов LLM не выполняется
     * @throws AppException если запрос пуст или превышает лимит
     */
    public PreparedPdfContext prepare(byte[] pdfContent, String userPrompt) {
        Objects.requireNonNull(pdfContent, "PDF content must not be null");
        if (userPrompt == null || userPrompt.isBlank()) {
            throw AppException.build(ErrorCode.INVALID_PROMPT, "Запрос пользователя не должен быть пустым");
        }
        String prompt = userPrompt.trim();
        if (tokenizer.countTokens(prompt) > properties.maxPromptTokens()) {
            throw AppException.build(ErrorCode.INVALID_PROMPT,
                    "Запрос пользователя превышает допустимый размер");
        }
        ranker.validatePrompt(prompt);
        ChunkedDocument document = chunkingPipeline.chunk(pdfContent);
        return assembler.assemble(prompt, ranker.rank(prompt, document.chunks()),
                document.chunks().size());
    }
}
