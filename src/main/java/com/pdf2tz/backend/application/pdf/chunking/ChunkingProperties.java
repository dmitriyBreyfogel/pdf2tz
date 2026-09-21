package com.pdf2tz.backend.application.pdf.chunking;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Серверная политика ограничения размера итогового document chunk.
 *
 * <p>Публичным параметром v0.2.0 является только общий token limit. Overlap,
 * минимальный размер и лимит числа чанков намеренно не вводятся до появления
 * реального сценария, который их оправдывает.</p>
 */
@Component
@ConfigurationProperties(prefix = "pdf2tz.chunking")
public class ChunkingProperties {

    /**
     * Минимальный практически полезный бюджет, при котором ещё помещается
     * структурный префикс и короткий body-текст.
     */
    private static final int MIN_SUPPORTED_TOKEN_LIMIT = 128;

    private int maxTokens = 1200;

    public int maxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        if (maxTokens < MIN_SUPPORTED_TOKEN_LIMIT) {
            throw new IllegalArgumentException(
                    "Chunk token limit must be at least " + MIN_SUPPORTED_TOKEN_LIMIT
            );
        }
        this.maxTokens = maxTokens;
    }
}
