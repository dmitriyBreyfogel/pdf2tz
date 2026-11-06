package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceDocumentServiceTest {
    @Test
    void createsSingleDeviceDocumentWithSampleTableHeaderAndRows() throws Exception {
        var plan = new ObjectMapper().readTree("""
                {"sections":[{"name":"Очистка","rows":[{"number":1,"deviceName":"Прибор","maintenanceType":"Очистка датчика","maintenanceWorkDescription":["Отключить питание.","Протереть датчик."],"registrationNumber":"РЗН 1","inventoryNumber":null,"frequency":"Еженедельно","sourcePages":[1]}]}],"consumables":[]}
                """);

        byte[] result = new MaintenanceDocumentService().create(plan);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertEquals("Приложение №1", document.getParagraphArray(0).getText());
            var table = document.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());
            assertEquals("№ п.п.", table.getRow(0).getCell(0).getText());
            assertEquals("Очистка", table.getRow(1).getCell(0).getText());
            assertEquals("Прибор", table.getRow(2).getCell(1).getText());
            assertTrue(table.getRow(2).getCell(2).getText().contains("Отключить питание."));
            assertEquals("", table.getRow(2).getCell(4).getText());
        }
    }
}
