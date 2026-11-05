package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MaintenancePlanService {
    private final PdfChunkingPipeline chunkingPipeline;
    private final RouterAiClient routerAiClient;
    private final MaintenanceChunkSelector chunkSelector;

    public MaintenancePlanService(PdfChunkingPipeline chunkingPipeline, RouterAiClient routerAiClient, MaintenanceChunkSelector chunkSelector) {
        this.chunkingPipeline = chunkingPipeline;
        this.routerAiClient = routerAiClient;
        this.chunkSelector = chunkSelector;
    }

    public JsonNode generate(byte[] pdf, String modelCode) {
        final MaintenanceLlmModel model;
        try { model = MaintenanceLlmModel.byRouterAiId(modelCode); }
        catch (IllegalArgumentException ex) { throw AppException.build(ErrorCode.UNSUPPORTED_LLM_MODEL, "Модель не разрешена для генерации ТЗ"); }
        ChunkedDocument document = chunkingPipeline.chunk(pdf);
        String context = select(document.chunks());
        JsonNode plan = routerAiClient.generate(model, instruction(), context);
        if (!plan.isObject() || !plan.has("sections") || !plan.has("consumables")) {
            throw AppException.build(ErrorCode.LLM_INVALID_RESPONSE, "Модель вернула JSON, не соответствующий плану ТЗ");
        }
        return clearInventoryNumbers(plan);
    }

    private String select(List<DocumentChunk> chunks) {
        List<DocumentChunk> selected = chunkSelector.select(chunks);
        return selected.stream().map(chunk -> "[CHUNK " + chunk.chunkNumber() + "; PAGES "
                + chunk.pageRange().startPageNumber() + "-" + chunk.pageRange().endPageNumber() + "]\n" + chunk.content())
                .reduce((left, right) -> left + "\n\n" + right).orElseThrow();
    }

    private String instruction() {
        return "Ты составляешь проект ТЗ технического обслуживания одного медицинского изделия только по переданным фрагментам инструкции. "
                + "Работай строго по предоставленным данным: не выдумывай, не додумывай и не обобщай факты, работы, периодичность, расходники или номера. "
                + "Если сведения не подтверждены фрагментами инструкции, не включай их в результат. Каждая строка верхнего уровня — ровно одно сочетание одного вида работ и одной конкретной периодичности либо условия выполнения. "
                + "Не объединяй в одной строке разные работы, периодичности, условия или режимы обслуживания; для каждого такого сочетания создай отдельную строку. "
                + "maintenanceWorkDescription опиши достаточно подробно по инструкции: перечисли существенные действия и порядок их выполнения, но без длинного пересказа. Его элементы могут быть только последовательными шагами одной работы из этой строки, а не самостоятельными работами или внутренними строками. "
                + "registrationNumber заполняй только если он явно указан в переданных фрагментах, иначе null. inventoryNumber всегда возвращай null: не ищи, не извлекай и не заполняй инвентарный номер. "
                + "Расходники не включай в строки ТЗ: верни отдельно и только если необходимость или норма расхода прямо следует из инструкции. "
                + "Верни JSON с полями sections и consumables. В sections[].rows[] обязательны number, deviceName, maintenanceType, maintenanceWorkDescription, registrationNumber, inventoryNumber, frequency, sourcePages. "
                + "В consumables[] обязательны name, purpose, relatedRowNumbers, consumptionRate, sourcePages. sourcePages всегда содержит страницы источника.";
    }

    private JsonNode clearInventoryNumbers(JsonNode plan) {
        plan.path("sections").forEach(section -> section.path("rows").forEach(row -> {
            if (row instanceof ObjectNode objectRow) {
                objectRow.putNull("inventoryNumber");
            }
        }));
        return plan;
    }
}
