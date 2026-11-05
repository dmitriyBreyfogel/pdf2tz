package com.pdf2tz.backend.infrastructure.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouterAiHttpClientTest {
    @Test
    void unwrapsJsonMarkdownFenceReturnedByProvider() {
        assertEquals("{\"sections\":[],\"consumables\":[]}", RouterAiHttpClient.jsonContent("""
                ```json
                {"sections":[],"consumables":[]}
                ```
                """));
    }

    @Test
    void preservesPlainJson() {
        assertEquals("{\"sections\":[]}", RouterAiHttpClient.jsonContent("  {\"sections\":[]}  "));
    }
}
