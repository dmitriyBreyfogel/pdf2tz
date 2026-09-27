package com.pdf2tz.backend.application.ports;

import java.util.List;

/**
 * Порт оценки и bounded-разбиения текста в токенах.
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

    /**
     * Делит текст на последовательные части не больше заданного лимита.
     *
     * @param text исходный текст
     * @param maxTokens максимальное число токенов в части
     * @return непустые части в исходном порядке
     */
    List<String> splitByTokenLimit(String text, int maxTokens);
}
