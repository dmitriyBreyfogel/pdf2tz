package com.pdf2tz.backend.api.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceModelControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MaintenanceModelController()).build();

    @Test
    void returnsOnlyReviewedRouterAiModels() throws Exception {
        mvc.perform(get("/api/v1/maintenance/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].modelCode").value("sber/gigachat-3.5-ultra"))
                .andExpect(jsonPath("$[1].schemaEnforced").value(false))
                .andExpect(jsonPath("$[5].modelCode").value("anthropic/claude-sonnet-5.5"));
    }
}
