package com.pdf2tz.backend.application.maintenance.model;

import java.util.Arrays;

/**
 * Разрешённые для составления ТЗ модели RouterAI.
 *
 * <p>Идентификаторы принадлежат каталогу RouterAI, а не приходят от клиента
 * произвольной строкой. Это не позволяет использовать ключ сервиса для
 * непроверенных или неожиданно дорогих моделей.</p>
 */
public enum MaintenanceLlmModel {
    GIGACHAT_ULTRA("sber/gigachat-3.5-ultra", "GigaChat 3.5 Ultra", "Сбер", true, 20000),
    YANDEX_GPT_LITE("yandex/gpt-lite-5", "YandexGPT Lite 5", "Яндекс", false, 20000),
    DEEPSEEK_FLASH("deepseek/deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "DeepSeek", true, 20000),
    QWEN_397B("qwen/qwen3.5-397b-a17b", "Qwen 3.5 397B A17B", "Qwen", true, 20000),
    GPT_TERRA("openai/gpt-6.1-terra", "GPT-6.1 Terra", "OpenAI", true, 20000),
    CLAUDE_SONNET("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", "Anthropic", true, 20000);

    private final String routerAiId;
    private final String displayName;
    private final String provider;
    private final boolean schemaEnforced;
    private final int maxCompletionTokens;

    MaintenanceLlmModel(String routerAiId, String displayName, String provider, boolean schemaEnforced, int maxCompletionTokens) {
        this.routerAiId = routerAiId;
        this.displayName = displayName;
        this.provider = provider;
        this.schemaEnforced = schemaEnforced;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public String routerAiId() { return routerAiId; }
    public String displayName() { return displayName; }
    public String provider() { return provider; }
    public boolean schemaEnforced() { return schemaEnforced; }
    public int maxCompletionTokens() { return maxCompletionTokens; }

    public static MaintenanceLlmModel byRouterAiId(String modelId) {
        return Arrays.stream(values())
                .filter(model -> model.routerAiId.equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported RouterAI model: " + modelId));
    }
}
