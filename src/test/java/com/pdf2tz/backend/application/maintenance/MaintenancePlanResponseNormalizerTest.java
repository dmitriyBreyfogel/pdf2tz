package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdf2tz.backend.error.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaintenancePlanResponseNormalizerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MaintenancePlanResponseNormalizer normalizer = new MaintenancePlanResponseNormalizer();

    @Test
    void renumbersRowsSequentiallyAndRemapsConsumableLinks() throws Exception {
        var response = mapper.readTree("""
                {"sections":[{"name":"Работы","rows":[{"number":2,"deviceName":"Прибор","maintenanceType":"Очистка","maintenanceWorkDescription":["Протереть."],"registrationNumber":null,"inventoryNumber":null,"frequency":"Еженедельно","sourcePages":[1]},{"number":5,"deviceName":"Прибор","maintenanceType":"Осмотр","maintenanceWorkDescription":["Осмотреть."],"registrationNumber":null,"inventoryNumber":null,"frequency":"Ежегодно","sourcePages":[2]}]}],"consumables":[{"name":"Салфетка","purpose":"Осмотр.","relatedRowNumbers":[5],"consumptionRate":null,"sourcePages":[2]}]}
                """);

        var plan = normalizer.normalize(response);

        assertEquals(1, plan.at("/sections/0/rows/0/number").asInt());
        assertEquals(2, plan.at("/sections/0/rows/1/number").asInt());
        assertEquals(2, plan.at("/consumables/0/relatedRowNumbers/0").asInt());
    }

    @Test
    void normalizesFlatProviderRowsWithoutChangingMaintenanceFacts() throws Exception {
        var response = mapper.readTree("""
                {"sections":[{"number":"1","deviceName":"Прибор","maintenanceType":"Очистка","maintenanceWorkDescription":"Протереть корпус.","registrationNumber":null,"inventoryNumber":"A-1","frequency":"Еженедельно","sourcePages":"23-24"}],"consumables":[{"name":"Салфетка","purpose":"Очистка корпуса.","relatedRowNumbers":"1","consumptionRate":null,"sourcePages":"24"}]}
                """);

        var plan = normalizer.normalize(response);

        assertEquals("Работы технического обслуживания", plan.at("/sections/0/name").asText());
        assertEquals("Протереть корпус.", plan.at("/sections/0/rows/0/maintenanceWorkDescription/0").asText());
        assertEquals(23, plan.at("/sections/0/rows/0/sourcePages/0").asInt());
        assertEquals(24, plan.at("/sections/0/rows/0/sourcePages/1").asInt());
        assertEquals(true, plan.at("/sections/0/rows/0/inventoryNumber").isNull());
    }

    @Test
    void rejectsConsumableThatDoesNotReferenceAnExistingRow() throws Exception {
        var response = mapper.readTree("""
                {"sections":[{"name":"Работы","rows":[{"number":1,"deviceName":"Прибор","maintenanceType":"Очистка","maintenanceWorkDescription":["Протереть."],"registrationNumber":null,"inventoryNumber":null,"frequency":"Еженедельно","sourcePages":[1]}]}],"consumables":[{"name":"Салфетка","purpose":"Очистка.","relatedRowNumbers":[2],"consumptionRate":null,"sourcePages":[1]}]}
                """);

        assertThrows(AppException.class, () -> normalizer.normalize(response));
    }
}
