package com.pdf2tz.backend.application.ports;

/**
 * Порт подсчёта токенов в готовом тексте чанка.
 *
 * <p>Application-слой использует этот контракт, не зная, какой tokenizer
 * соответствует выбранной в infrastructure модели.</p>
 */
public interface LlmTokenizerPort {

    /**
     * Считает токены в точной строке payload.
     *
     * @param text текст
     * @return количество токенов
     */
    int countTokens(String text);
}
