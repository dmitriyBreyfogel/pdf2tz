package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.pdf2tz.backend.application.maintenance.model.MaintenanceLlmModel;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MaintenancePlanService {
    private final PdfChunkingPipeline chunkingPipeline;
    private final RouterAiClient routerAiClient;
    private final MaintenanceChunkSelector chunkSelector;
    private final MaintenancePlanResponseNormalizer responseNormalizer;
    private final MaintenancePlanDiagnosticsProperties diagnosticsProperties;

    public MaintenancePlanService(PdfChunkingPipeline chunkingPipeline, RouterAiClient routerAiClient,
                                  MaintenanceChunkSelector chunkSelector, MaintenancePlanResponseNormalizer responseNormalizer,
                                  MaintenancePlanDiagnosticsProperties diagnosticsProperties) {
        this.chunkingPipeline = chunkingPipeline;
        this.routerAiClient = routerAiClient;
        this.chunkSelector = chunkSelector;
        this.responseNormalizer = responseNormalizer;
        this.diagnosticsProperties = diagnosticsProperties;
    }

    public JsonNode generate(byte[] pdf, String modelCode) {
        final MaintenanceLlmModel model;
        try { model = MaintenanceLlmModel.byRouterAiId(modelCode); }
        catch (IllegalArgumentException ex) { throw AppException.build(ErrorCode.UNSUPPORTED_LLM_MODEL, "Модель не разрешена для генерации ТЗ"); }
        ChunkedDocument document = chunkingPipeline.chunk(pdf);
        String context = select(document.chunks());
        JsonNode plan = routerAiClient.generate(model, instruction(), context);
        try {
            return responseNormalizer.normalize(plan);
        } catch (AppException exception) {
            if (!diagnosticsProperties.includeInvalidResponse() || exception.getCode() != ErrorCode.LLM_INVALID_RESPONSE) {
                throw exception;
            }
            throw AppException.build(ErrorCode.LLM_INVALID_RESPONSE, exception.getMessage(),
                    Map.of("modelCode", model.routerAiId(), "providerResponse", plan));
        }
    }

    private String select(List<DocumentChunk> chunks) {
        List<DocumentChunk> selected = chunkSelector.select(chunks);
        return selected.stream().map(chunk -> "[CHUNK " + chunk.chunkNumber() + "; PAGES "
                + chunk.pageRange().startPageNumber() + "-" + chunk.pageRange().endPageNumber() + "]\n" + chunk.content())
                .reduce((left, right) -> left + "\n\n" + right).orElseThrow();
    }

    private String instruction() {
        return """
                Ты составляешь проект ТЗ технического обслуживания одного медицинского изделия только по переданным фрагментам инструкции. Ответ всегда на русском языке.

                Источник истины — только фрагменты после этого сообщения. Не выдумывай, не додумывай, не обобщай и не подменяй факты, работы, периодичность, расходники, номера, параметры или предупреждения. Если работа, расходник или подробность не подтверждены фрагментами, не включай их.

                Сначала молча выдели все подтверждённые работы по техническому обслуживанию, очистке, дезинфекции, осмотру, проверке, замене и подготовке к обслуживанию. Затем составь строки. Каждая строка верхнего уровня — ровно одно сочетание одной работы и одной конкретной периодичности либо одного условия выполнения. Не объединяй разные работы, периодичности, условия или режимы в одну строку. Если одна и та же работа выполняется регулярно и после события, это две разные строки. Не допускай в frequency объединений условий через «и», «или», запятую либо косую черту: например, «до и после дезинфекции» требует двух самостоятельных строк. maintenanceType — название ровно одной конкретной работы, а не общее название группы работ. Не создавай внутренние строки или самостоятельные работы внутри maintenanceWorkDescription: его элементы могут быть только последовательными шагами выполнения одной работы этой строки.

                Определи одно короткое основное наименование медицинского изделия из инструкции: тип изделия, производитель и модель — без регистрационных каталогов, перечней принадлежностей, юридических формулировок и длинных технических описаний. Например, «Видеоголовка эндоскопическая C-CAM». В deviceName каждой строки верни это наименование абсолютно одинаковой строкой: оно будет показано в каждой строке итоговой таблицы и в её заголовке. Не используй для deviceName названия узлов, принадлежностей, режимов или отдельных частей изделия. sections — только техническая оболочка JSON: верни ровно одну секцию с именем "Работы технического обслуживания" и не группируй в ней работы тематическими заголовками.

                Изделие эксплуатируется в Российской Федерации. Включай обслуживание самого изделия и всех относящихся к нему принадлежностей, многоразовых инструментов и функциональных компонентов, если инструкция требует их обслуживания. Если инструкция разделяет порядок работ для США и для рынков вне США, выбери только вариант для рынков вне США как применимый к РФ. Не включай работы, режимы, условия или расходники, помеченные только для США. Не упоминай в maintenanceType, maintenanceWorkDescription или frequency названия стран, рынков и территориальные оговорки: вместо «ежедневно за пределами США» используй «Ежедневно». Если региональные варианты описывают одну и ту же работу, верни только одну применимую к РФ строку с её фактическими действиями.

                maintenanceWorkDescription — понятная исполнителю, подробная, но компактная инструкция, а не краткое название работы. Сохрани все практически значимые сведения из фрагментов: подготовку и доступ к детали, снятие и установку элементов, порядок действий, разрешённые средства и инструменты, параметры и усилия, ограничения, запреты, меры предосторожности, признаки корректного результата, сборку и проверку после работы. Не теряй относящиеся к работе сведения, но не пересказывай не относящееся к ней содержание инструкции.

                Если сама работа подтверждена, но её периодичность в переданных фрагментах не названа, сохрани работу с frequency ровно "Не указана в инструкции". Не придумывай периодичность. registrationNumber заполняй только если он явно указан в фрагментах, иначе null. inventoryNumber всегда возвращай null: не ищи и не заполняй инвентарный номер.

                Расходник — расходуемый материал, одноразовое изделие, жидкость, реагент или заменяемый элемент, который прямо требуется для подтверждённой работы. Не добавляй расходник по догадке. Не включай многоразовый инструмент или часть прибора, если инструкция не предписывает её замену. Расходники не дублируй в строках ТЗ: верни их только в consumables. consumptionRate указывай только при явно названной норме или условии расхода, иначе null. relatedRowNumbers должны ссылаться на номера связанных строк. Перед ответом обязательно пройди каждое описание работ: каждый явно упомянутый в нём требуемый расходуемый материал должен иметь одну связанную запись в consumables. Если описание содержит моющее или дезинфицирующее средство, воду, спирт, салфетку, ткань, ватный аппликатор, тест-полоску, упаковку, стерилизационный агент либо иной прямо требуемый расходный материал, пустой consumables недопустим.

                Для каждой строки и каждого расходника укажи sourcePages только из маркеров PAGES переданных фрагментов. Номера строк должны быть уникальными положительными целыми числами.

                Ответ разбирается программой без участия человека: соблюдение JSON-контракта — обязательная часть задачи. Верни только один JSON-объект, без Markdown, комментариев, пояснений, обёрток или текста до и после JSON. Корневой объект содержит РОВНО два поля: sections и consumables. Не переименовывай поля, не добавляй поля и не пропускай ни одного обязательного поля, даже когда его значение неизвестно: для registrationNumber и consumptionRate используй null; для отсутствующих расходников используй пустой массив consumables.

                Строго соблюдай эту форму: {"sections":[{"name":"...","rows":[{"number":1,"deviceName":"...","maintenanceType":"...","maintenanceWorkDescription":["шаг 1","шаг 2"],"registrationNumber":null,"inventoryNumber":null,"frequency":"...","sourcePages":[1]}]}],"consumables":[{"name":"...","purpose":"...","relatedRowNumbers":[1],"consumptionRate":null,"sourcePages":[1]}]}. Никогда не помещай строки напрямую в sections: каждая секция обязана иметь непустой name и непустой массив rows. У каждой строки обязательны РОВНО поля number, deviceName, maintenanceType, maintenanceWorkDescription, registrationNumber, inventoryNumber, frequency, sourcePages. У каждого расходника обязательны РОВНО поля name, purpose, relatedRowNumbers, consumptionRate, sourcePages. maintenanceWorkDescription, sourcePages и relatedRowNumbers всегда массивы; number, relatedRowNumbers и номера страниц всегда положительные целые числа, а не строки. maintenanceWorkDescription и sourcePages не бывают пустыми. inventoryNumber всегда именно null, не строка и не число.

                Перед отправкой молча проверь весь ответ: JSON разбирается; есть только sections и consumables; все секции содержат rows; каждая строка и каждый расходник имеют все поля указанной формы и корректные типы; inventoryNumber равен null; номера строк уникальны и положительны; все ссылки relatedRowNumbers существуют; в frequency нет объединённых условий; все явно требуемые расходники из описаний работ перенесены в consumables; в ответе нет неподтверждённых сведений. Не отправляй частичный JSON: сначала закончи проверку, затем выведи весь JSON одним сообщением.
                """;
    }
}
