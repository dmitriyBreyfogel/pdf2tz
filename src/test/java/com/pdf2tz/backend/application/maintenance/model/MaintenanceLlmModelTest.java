package com.pdf2tz.backend.application.maintenance.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceLlmModelTest {
    @Test
    void reservesEnoughCompletionTokensForReasoningModels() {
        assertEquals(16000, MaintenanceLlmModel.GIGACHAT_ULTRA.maxCompletionTokens());
        assertEquals(16000, MaintenanceLlmModel.DEEPSEEK_FLASH.maxCompletionTokens());
        assertEquals(16000, MaintenanceLlmModel.QWEN_397B.maxCompletionTokens());
        assertEquals(8000, MaintenanceLlmModel.GPT_TERRA.maxCompletionTokens());
    }
}
