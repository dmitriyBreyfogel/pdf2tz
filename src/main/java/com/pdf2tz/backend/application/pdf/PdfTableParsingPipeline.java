package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.table.PdfTableParsingService;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Координирует извлечение таблиц из PDF-документа для API-слоя.
 *
 * <p>Pipeline строит профиль служебного шума по текстовому слою документа и
 * передаёт его в табличный flow. Благодаря этому watermark-и и повторяющиеся
 * служебные фрагменты очищаются в ячейках таблиц тем же правилом, что и в
 * обычном тексте документа.</p>
 */
@Service
public class PdfTableParsingPipeline {

    private final PdfReaderPort pdfReaderPort;
    private final DocumentNoiseProfileBuilder documentNoiseProfileBuilder;
    private final PdfTableParsingService pdfTableParsingService;

    public PdfTableParsingPipeline(
            PdfReaderPort pdfReaderPort,
            DocumentNoiseProfileBuilder documentNoiseProfileBuilder,
            PdfTableParsingService pdfTableParsingService
    ) {
        this.pdfReaderPort = Objects.requireNonNull(pdfReaderPort, "PDF reader port must not be null");
        this.documentNoiseProfileBuilder = Objects.requireNonNull(
                documentNoiseProfileBuilder,
                "Document noise profile builder must not be null"
        );
        this.pdfTableParsingService = Objects.requireNonNull(
                pdfTableParsingService,
                "PDF table parsing service must not be null"
        );
    }

    /**
     * Извлекает из PDF-документа целостные очищенные таблицы.
     *
     * @param content байтовое представление PDF-файла
     * @return таблицы документа
     */
    public List<ParsedTable> parseTables(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");

        ExtractedDocument extractedDocument = pdfReaderPort.read(content);
        DocumentNoiseProfile noiseProfile = documentNoiseProfileBuilder.build(extractedDocument);

        return pdfTableParsingService.parseTables(content, noiseProfile);
    }
}
