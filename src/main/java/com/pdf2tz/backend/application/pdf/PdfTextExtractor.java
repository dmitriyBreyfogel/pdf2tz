package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PdfTextExtractor {
    private final PdfReaderPort pdfReaderPort;
    private final DocumentNoiseProfileBuilder documentNoiseProfileBuilder;
    private final TextCleaner textCleaner;

    public PdfTextExtractor(
            PdfReaderPort pdfReaderPort,
            DocumentNoiseProfileBuilder documentNoiseProfileBuilder,
            TextCleaner textCleaner
    ) {
        this.pdfReaderPort = pdfReaderPort;
        this.documentNoiseProfileBuilder = documentNoiseProfileBuilder;
        this.textCleaner = textCleaner;
    }

    /**
     * Извлекает и очищает текстовое содержимое PDF-документа.
     *
     * @param content байтовое представление PDF-файла
     * @return очищенное содержимое документа
     */
    public CleanedDocument extract(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");

        ExtractedDocument extractedDocument = pdfReaderPort.read(content);
        DocumentNoiseProfile noiseProfile = documentNoiseProfileBuilder.build(extractedDocument);

        return textCleaner.cleanDocument(extractedDocument, noiseProfile);
    }
}
