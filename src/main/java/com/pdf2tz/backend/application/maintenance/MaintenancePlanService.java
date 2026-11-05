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

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class MaintenancePlanService {
    private static final List<String> MARKERS = List.of("техническ", "обслужив", "периодич", "очист", "дезинф", "провер", "замен", "maintenance", "clean", "inspection", "service");
    private final PdfChunkingPipeline chunkingPipeline;
    private final RouterAiClient routerAiClient;

    public MaintenancePlanService(PdfChunkingPipeline chunkingPipeline, RouterAiClient routerAiClient) {
        this.chunkingPipeline = chunkingPipeline;
        this.routerAiClient = routerAiClient;
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
        List<DocumentChunk> relevant = chunks.stream()
                .sorted(Comparator.<DocumentChunk>comparingInt(chunk -> score(chunk.content())).reversed()
                        .thenComparingInt(DocumentChunk::chunkNumber))
                .filter(chunk -> score(chunk.content()) > 0).limit(18).sorted(Comparator.comparingInt(DocumentChunk::chunkNumber)).toList();
        List<DocumentChunk> selected = relevant.isEmpty() ? chunks.stream().limit(18).toList() : relevant;
        return selected.stream().map(chunk -> "[CHUNK " + chunk.chunkNumber() + "; PAGES "
                + chunk.pageRange().startPageNumber() + "-" + chunk.pageRange().endPageNumber() + "]\n" + chunk.content())
                .reduce((left, right) -> left + "\n\n" + right).orElseThrow();
    }

    private int score(String content) {
        String lower = content.toLowerCase(Locale.ROOT);
        return (int) MARKERS.stream().filter(lower::contains).count();
    }

    private String instruction() {
        return "Ты составляешь проект ТЗ технического обслуживания одного медицинского изделия только по переданным фрагментам инструкции. "
                + "Работай строго по предоставленным данным: не выдумывай, не додумывай и не обобщай факты, работы, периодичность, расходники или номера. "
                + "Если сведения не подтверждены фрагментами инструкции, не включай их в результат. Каждая строка — отдельное сочетание вида работ и периодичности. "
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
