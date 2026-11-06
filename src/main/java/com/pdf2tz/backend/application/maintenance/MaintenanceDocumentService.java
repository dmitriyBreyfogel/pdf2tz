package com.pdf2tz.backend.application.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTVMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/** Creates the single-device maintenance table in the layout of the supplied contract sample. */
@Service
public class MaintenanceDocumentService {
    private static final String FONT = "Times New Roman";
    private static final int[] WIDTHS = {480, 1650, 3300, 1050, 750, 1410};
    private static final int CELL_MARGIN_HORIZONTAL = 100;
    private static final int CELL_MARGIN_VERTICAL = 70;

    public byte[] create(JsonNode plan) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(document);
            String deviceName = deviceName(plan);
            String registrationNumber = registrationNumber(plan);
            paragraph(document, "Перечень услуг по техническому обслуживанию медицинского изделия: " + deviceName,
                    ParagraphAlignment.CENTER, 10, true);
            XWPFTable table = document.createTable(1, WIDTHS.length);
            configureTable(table);
            writeHeader(table.getRow(0));
            int firstWorkRow = table.getNumberOfRows();
            for (JsonNode section : plan.path("sections")) {
                for (JsonNode row : section.path("rows")) {
                    writeWork(table, row, deviceName, registrationNumber);
                }
            }
            mergeCommonDeviceColumns(table, firstWorkRow);
            document.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Не удалось сформировать DOCX-файл ТЗ", exception);
        }
    }

    private void configurePage(XWPFDocument document) {
        var section = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr() : document.getDocument().getBody().addNewSectPr();
        section.addNewPgSz().setW(BigInteger.valueOf(12240));
        section.getPgSz().setH(BigInteger.valueOf(15840));
        var margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440)); margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1800)); margins.setRight(BigInteger.valueOf(1800));
    }

    private void configureTable(XWPFTable table) {
        table.setWidth("8640");
        var properties = table.getCTTbl().getTblPr();
        properties.addNewTblLayout().setType(STTblLayoutType.FIXED);
        CTTblBorders borders = properties.addNewTblBorders();
        for (CTBorder border : new CTBorder[]{borders.addNewTop(), borders.addNewBottom(), borders.addNewLeft(), borders.addNewRight(), borders.addNewInsideH(), borders.addNewInsideV()}) {
            border.setVal(STBorder.SINGLE); border.setSz(BigInteger.valueOf(4)); border.setColor("000000");
        }
        CTTblGrid grid = table.getCTTbl().addNewTblGrid();
        for (int width : WIDTHS) grid.addNewGridCol().setW(BigInteger.valueOf(width));
    }

    private void writeHeader(XWPFTableRow row) {
        String[] headings = {"№ п.п.", "Наименование МИ", "Перечень выполняемых работ", "Регистрационный номер медицинского изделия", "Инвентарный номер", "Периодичность технического обслуживания"};
        CTTrPr properties = row.getCtRow().addNewTrPr();
        CTOnOff repeat = properties.addNewTblHeader(); repeat.setVal(true);
        row.setHeightRule(TableRowHeightRule.AT_LEAST);
        for (int index = 0; index < headings.length; index++) cell(row.getCell(index), headings[index], 8, true, ParagraphAlignment.CENTER);
    }

    private void writeWork(XWPFTable table, JsonNode work, String deviceName, String registrationNumber) {
        XWPFTableRow row = table.createRow();
        cell(row.getCell(0), work.path("number").asText(), 8, false, ParagraphAlignment.CENTER);
        cell(row.getCell(1), deviceName, 8, false, ParagraphAlignment.LEFT);
        workCell(row.getCell(2), work);
        cell(row.getCell(3), registrationNumber, 8, false, ParagraphAlignment.CENTER);
        cell(row.getCell(4), "", 8, false, ParagraphAlignment.CENTER);
        cell(row.getCell(5), work.path("frequency").asText(), 8, false, ParagraphAlignment.CENTER);
    }

    private void workCell(XWPFTableCell cell, JsonNode work) {
        clear(cell);
        applyMargins(cell);
        paragraph(cell, work.path("maintenanceType").asText(), ParagraphAlignment.LEFT, 8, true);
        for (JsonNode step : work.path("maintenanceWorkDescription")) {
            paragraph(cell, "— " + step.asText(), ParagraphAlignment.LEFT, 8, false);
        }
    }

    private void cell(XWPFTableCell cell, String text, int size, boolean bold, ParagraphAlignment alignment) {
        clear(cell);
        applyMargins(cell);
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        paragraph(cell, text, alignment, size, bold);
    }

    private void clear(XWPFTableCell cell) {
        while (cell.getParagraphs().size() > 1) cell.removeParagraph(1);
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) paragraph.removeRun(index);
    }

    private void paragraph(XWPFDocument document, String text, ParagraphAlignment alignment, int size, boolean bold) {
        paragraph(document.createParagraph(), text, alignment, size, bold);
    }

    private void paragraph(XWPFTableCell cell, String text, ParagraphAlignment alignment, int size, boolean bold) {
        XWPFParagraph paragraph = cell.getParagraphs().get(0).getRuns().isEmpty() && cell.getParagraphs().size() == 1
                ? cell.getParagraphs().get(0) : cell.addParagraph();
        paragraph(paragraph, text, alignment, size, bold);
    }

    private void paragraph(XWPFParagraph paragraph, String text, ParagraphAlignment alignment, int size, boolean bold) {
        paragraph.setAlignment(alignment); paragraph.setSpacingAfter(0); paragraph.setSpacingBefore(0); paragraph.setSpacingBetween(1.0);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(FONT); run.setFontSize(size); run.setBold(bold); run.setText(text);
    }

    private String deviceName(JsonNode plan) {
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode row : section.path("rows")) {
                String name = row.path("deviceName").asText().trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        throw new IllegalArgumentException("План ТЗ не содержит наименование медицинского изделия");
    }

    private String registrationNumber(JsonNode plan) {
        for (JsonNode section : plan.path("sections")) {
            for (JsonNode row : section.path("rows")) {
                JsonNode registrationNumber = row.path("registrationNumber");
                if (!registrationNumber.isNull() && !registrationNumber.asText().isBlank()) {
                    return registrationNumber.asText().trim();
                }
            }
        }
        return "";
    }

    private void mergeCommonDeviceColumns(XWPFTable table, int firstWorkRow) {
        int lastWorkRow = table.getNumberOfRows() - 1;
        if (lastWorkRow < firstWorkRow) {
            return;
        }
        for (int column : new int[]{1, 3, 4}) {
            for (int row = firstWorkRow; row <= lastWorkRow; row++) {
                XWPFTableCell cell = table.getRow(row).getCell(column);
                CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
                CTVMerge merge = properties.isSetVMerge() ? properties.getVMerge() : properties.addNewVMerge();
                merge.setVal(row == firstWorkRow ? STMerge.RESTART : STMerge.CONTINUE);
            }
        }
    }

    private void applyMargins(XWPFTableCell cell) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcMar margins = properties.isSetTcMar() ? properties.getTcMar() : properties.addNewTcMar();
        margin(margins.isSetTop() ? margins.getTop() : margins.addNewTop(), CELL_MARGIN_VERTICAL);
        margin(margins.isSetBottom() ? margins.getBottom() : margins.addNewBottom(), CELL_MARGIN_VERTICAL);
        margin(margins.isSetLeft() ? margins.getLeft() : margins.addNewLeft(), CELL_MARGIN_HORIZONTAL);
        margin(margins.isSetRight() ? margins.getRight() : margins.addNewRight(), CELL_MARGIN_HORIZONTAL);
    }

    private void margin(CTTblWidth value, int width) {
        value.setType(STTblWidth.DXA);
        value.setW(BigInteger.valueOf(width));
    }
}
