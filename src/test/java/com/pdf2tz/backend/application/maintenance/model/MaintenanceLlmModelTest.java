package com.pdf2tz.backend.application.maintenance.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceLlmModelTest {
    @Test
    void reservesTwentyThousandCompletionTokensForEverySupportedModel() {
        for (MaintenanceLlmModel model : MaintenanceLlmModel.values()) {
            assertEquals(20000, model.maxCompletionTokens());
        }
    }

    @Test
    void reservesLowReasoningEffortForDeepSeekPlanGeneration() {
        assertEquals("low", MaintenanceLlmModel.DEEPSEEK_FLASH.reasoningEffort());
    }
}
