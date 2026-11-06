package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceDocumentServiceTest {
    @Test
    void createsSingleDeviceDocumentWithUnsectionedRowsAndCellMargins() throws Exception {
        var plan = new ObjectMapper().readTree("""
                {"sections":[{"name":"Очистка","rows":[{"number":1,"deviceName":"Медицинский прибор","maintenanceType":"Очистка датчика","maintenanceWorkDescription":["Отключить питание.","Протереть датчик."],"registrationNumber":"РЗН 1","inventoryNumber":null,"frequency":"Еженедельно","sourcePages":[1]}]},{"name":"Осмотр","rows":[{"number":2,"deviceName":"Вариант названия","maintenanceType":"Осмотр корпуса","maintenanceWorkDescription":["Осмотреть корпус."],"registrationNumber":"РЗН 1","inventoryNumber":null,"frequency":"Ежегодно","sourcePages":[2]}]}],"consumables":[]}
                """);

        byte[] result = new MaintenanceDocumentService().create(plan);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result))) {
            assertEquals("Перечень услуг по техническому обслуживанию медицинского изделия: Медицинский прибор", document.getParagraphArray(0).getText());
            var table = document.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());
            assertEquals("№ п.п.", table.getRow(0).getCell(0).getText());
            assertEquals("Медицинский прибор", table.getRow(1).getCell(1).getText());
            assertEquals("Медицинский прибор", table.getRow(2).getCell(1).getText());
            assertTrue(table.getRow(1).getCell(2).getText().contains("Очистка датчика"));
            assertTrue(table.getRow(1).getCell(2).getText().contains("Отключить питание."));
            assertTrue(table.getRow(1).getCell(2).getParagraphArray(0).getRuns().get(0).isBold());
            assertEquals("", table.getRow(1).getCell(4).getText());
            assertEquals(new java.math.BigInteger("100"), table.getRow(1).getCell(0).getCTTc().getTcPr().getTcMar().getLeft().getW());
            assertEquals(new java.math.BigInteger("70"), table.getRow(1).getCell(0).getCTTc().getTcPr().getTcMar().getTop().getW());
            assertEquals(new java.math.BigInteger("100"), table.getRow(1).getCell(2).getCTTc().getTcPr().getTcMar().getLeft().getW());
            assertEquals(new java.math.BigInteger("100"), table.getRow(1).getCell(2).getCTTc().getTcPr().getTcMar().getRight().getW());
            assertEquals(new java.math.BigInteger("70"), table.getRow(1).getCell(2).getCTTc().getTcPr().getTcMar().getTop().getW());
            assertEquals(new java.math.BigInteger("70"), table.getRow(1).getCell(2).getCTTc().getTcPr().getTcMar().getBottom().getW());
        }
    }
}
