package com.pdf2tz.backend.application.pdf.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Лимиты подготовки одного запроса к будущему LLM-этапу.
 * Бюджет включает текст запроса, маркеры источников и выбранные чанки.
 * Значения по умолчанию вмещают несколько чанков по 1200 токенов и оставляют
 * место для запроса; конкретная модель пока не определена, поэтому лимиты настраиваются.
 */
@Component
@ConfigurationProperties(prefix = "pdf2tz.retrieval")
public class RetrievalProperties {

    private int maxContextTokens = 8192;
    private int maxPromptTokens = 1024;
    private int maxChunks = 6;

    public int maxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        if (maxContextTokens < 128) {
            throw new IllegalArgumentException("Context token budget must be at least 128");
        }
        this.maxContextTokens = maxContextTokens;
    }

    public int maxPromptTokens() {
        return maxPromptTokens;
    }

    public void setMaxPromptTokens(int maxPromptTokens) {
        if (maxPromptTokens < 1) {
            throw new IllegalArgumentException("Prompt token budget must be positive");
        }
        this.maxPromptTokens = maxPromptTokens;
    }

    public int maxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        if (maxChunks < 1) {
            throw new IllegalArgumentException("Maximum selected chunk count must be positive");
        }
        this.maxChunks = maxChunks;
    }
}
