package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings harmless provider-specific JSON variations to the public plan contract.
 *
 * <p>The normalizer never invents a maintenance fact. It only reshapes scalar
 * fields into arrays, converts explicitly written page ranges to page numbers,
 * and wraps a flat list of rows into one section. Any missing required fact or
 * incompatible shape is rejected before it reaches an API caller.</p>
 */
@Component
public class MaintenancePlanResponseNormalizer {
    private static final Pattern NUMBER_OR_RANGE = Pattern.compile("(\\d+)\\s*(?:[-–—]\\s*(\\d+))?");
    private static final String FLAT_SECTION_NAME = "Работы технического обслуживания";

    public JsonNode normalize(JsonNode response) {
        if (!(response instanceof ObjectNode root) || !root.path("sections").isArray() || !root.path("consumables").isArray()) {
            throw invalid();
        }

        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        ArrayNode sections = normalized.putArray("sections");
        ArrayNode inputSections = (ArrayNode) root.path("sections");
        if (isFlatRows(inputSections)) {
            ObjectNode section = sections.addObject();
            section.put("name", FLAT_SECTION_NAME);
            normalizeRows(inputSections, section.putArray("rows"));
        } else {
            for (JsonNode inputSection : inputSections) {
                if (!(inputSection instanceof ObjectNode sectionObject) || !sectionObject.path("rows").isArray()) {
                    throw invalid();
                }
                ObjectNode section = sections.addObject();
                section.put("name", requiredText(sectionObject, "name"));
                normalizeRows((ArrayNode) sectionObject.path("rows"), section.putArray("rows"));
            }
        }

        Set<Integer> rowNumbers = rowNumbers(sections);
        ArrayNode consumables = normalized.putArray("consumables");
        for (JsonNode inputConsumable : root.path("consumables")) {
            if (!(inputConsumable instanceof ObjectNode consumableObject)) {
                throw invalid();
            }
            ObjectNode consumable = consumables.addObject();
            consumable.put("name", requiredText(consumableObject, "name"));
            consumable.put("purpose", requiredText(consumableObject, "purpose"));
            ArrayNode relatedRows = consumable.putArray("relatedRowNumbers");
            for (int number : positiveNumbers(consumableObject.get("relatedRowNumbers"))) {
                if (!rowNumbers.contains(number)) {
                    throw invalid();
                }
                relatedRows.add(number);
            }
            JsonNode rate = consumableObject.get("consumptionRate");
            if (rate == null || rate.isNull() || "null".equalsIgnoreCase(rate.asText())) {
                consumable.putNull("consumptionRate");
            } else {
                consumable.put("consumptionRate", requiredText(consumableObject, "consumptionRate"));
            }
            positiveNumbers(consumableObject.get("sourcePages")).forEach(consumable.putArray("sourcePages")::add);
        }
        return normalized;
    }

    private boolean isFlatRows(ArrayNode sections) {
        if (sections.isEmpty()) {
            return false;
        }
        for (JsonNode section : sections) {
            if (!section.isObject() || !section.has("number") || section.has("rows")) {
                return false;
            }
        }
        return true;
    }

    private void normalizeRows(ArrayNode inputRows, ArrayNode rows) {
        if (inputRows.isEmpty()) {
            throw invalid();
        }
        for (JsonNode inputRow : inputRows) {
            if (!(inputRow instanceof ObjectNode rowObject)) {
                throw invalid();
            }
            ObjectNode row = rows.addObject();
            row.put("number", requiredPositiveNumber(rowObject.get("number")));
            row.put("deviceName", requiredText(rowObject, "deviceName"));
            row.put("maintenanceType", requiredText(rowObject, "maintenanceType"));
            ArrayNode description = row.putArray("maintenanceWorkDescription");
            appendDescription(rowObject.get("maintenanceWorkDescription"), description);
            JsonNode registration = rowObject.get("registrationNumber");
            if (registration == null || registration.isNull() || "null".equalsIgnoreCase(registration.asText())) {
                row.putNull("registrationNumber");
            } else {
                row.put("registrationNumber", requiredText(rowObject, "registrationNumber"));
            }
            row.putNull("inventoryNumber");
            row.put("frequency", requiredText(rowObject, "frequency"));
            positiveNumbers(rowObject.get("sourcePages")).forEach(row.putArray("sourcePages")::add);
        }
    }

    private void appendDescription(JsonNode source, ArrayNode target) {
        if (source == null || source.isNull()) {
            throw invalid();
        }
        if (source.isTextual()) {
            target.add(requiredText(source));
            return;
        }
        if (!source.isArray() || source.isEmpty()) {
            throw invalid();
        }
        for (JsonNode item : source) {
            target.add(requiredText(item));
        }
    }

    private Set<Integer> rowNumbers(ArrayNode sections) {
        Set<Integer> numbers = new LinkedHashSet<>();
        for (JsonNode section : sections) {
            for (JsonNode row : section.path("rows")) {
                int number = row.path("number").asInt();
                if (!numbers.add(number)) {
                    throw invalid();
                }
            }
        }
        return numbers;
    }

    private Set<Integer> positiveNumbers(JsonNode value) {
        Set<Integer> result = new LinkedHashSet<>();
        appendPositiveNumbers(value, result);
        if (result.isEmpty()) {
            throw invalid();
        }
        return result;
    }

    private void appendPositiveNumbers(JsonNode value, Set<Integer> target) {
        if (value == null || value.isNull()) {
            throw invalid();
        }
        if (value.isIntegralNumber()) {
            addNumber(target, value.asInt());
            return;
        }
        if (value.isTextual()) {
            Matcher matcher = NUMBER_OR_RANGE.matcher(value.asText());
            boolean matched = false;
            while (matcher.find()) {
                matched = true;
                int start = parsePositive(matcher.group(1));
                int end = matcher.group(2) == null ? start : parsePositive(matcher.group(2));
                if (end < start || end - start > 100) {
                    throw invalid();
                }
                for (int number = start; number <= end; number++) {
                    target.add(number);
                }
            }
            if (!matched) {
                throw invalid();
            }
            return;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                appendPositiveNumbers(item, target);
            }
            return;
        }
        throw invalid();
    }

    private int requiredPositiveNumber(JsonNode value) {
        Set<Integer> numbers = positiveNumbers(value);
        if (numbers.size() != 1) {
            throw invalid();
        }
        return numbers.iterator().next();
    }

    private int parsePositive(String value) {
        try {
            int number = Integer.parseInt(value);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // Converted into a contract error below.
        }
        throw invalid();
    }

    private void addNumber(Set<Integer> target, int value) {
        if (value <= 0) {
            throw invalid();
        }
        target.add(value);
    }

    private String requiredText(ObjectNode node, String field) {
        return requiredText(node.get(field));
    }

    private String requiredText(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid();
        }
        return value.asText().trim();
    }

    private AppException invalid() {
        return AppException.build(ErrorCode.LLM_INVALID_RESPONSE, "Модель вернула JSON, не соответствующий плану ТЗ");
    }
}
