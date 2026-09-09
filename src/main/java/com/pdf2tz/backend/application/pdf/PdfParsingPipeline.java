package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.assembly.ParsedDocumentAssembler;
import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Координирует подготовку PDF-документа к дальнейшей обработке.
 *
 * <p>На текущей итерации pipeline читает PDF, строит профиль служебного шума,
 * очищает текст и собирает готовую блочную модель документа. Извлечение
 * таблиц будет подключено отдельным шагом позже.</p>
 */
@Service
public class PdfParsingPipeline {

    private final PdfReaderPort pdfReaderPort;
    private final DocumentNoiseProfileBuilder documentNoiseProfileBuilder;
    private final TextCleaner textCleaner;
    private final ParsedDocumentAssembler parsedDocumentAssembler;

    public PdfParsingPipeline(
            PdfReaderPort pdfReaderPort,
            DocumentNoiseProfileBuilder documentNoiseProfileBuilder,
            TextCleaner textCleaner,
            ParsedDocumentAssembler parsedDocumentAssembler
    ) {
        this.pdfReaderPort = pdfReaderPort;
        this.documentNoiseProfileBuilder = documentNoiseProfileBuilder;
        this.textCleaner = textCleaner;
        this.parsedDocumentAssembler = parsedDocumentAssembler;
    }

    /**
     * Преобразует PDF-файл в готовую блочную модель документа.
     *
     * @param content байтовое представление PDF-файла
     * @return готовый распарсенный документ
     */
    public ParsedDocument parse(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");

        ExtractedDocument extractedDocument = pdfReaderPort.read(content);
        DocumentNoiseProfile noiseProfile = documentNoiseProfileBuilder.build(extractedDocument);
        CleanedDocument cleanedDocument = textCleaner.cleanDocument(extractedDocument, noiseProfile);

        return parsedDocumentAssembler.assemble(cleanedDocument);
    }
}
