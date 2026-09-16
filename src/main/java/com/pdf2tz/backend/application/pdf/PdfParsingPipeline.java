package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.assembly.ParsedDocumentAssembler;
import com.pdf2tz.backend.application.pdf.cleaning.DocumentNoiseProfileBuilder;
import com.pdf2tz.backend.application.pdf.cleaning.TextCleaner;
import com.pdf2tz.backend.application.pdf.model.ExtractedTextDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.table.PdfTableParsingService;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Координирует подготовку PDF-документа к дальнейшей обработке.
 *
 * <p>Pipeline читает PDF, строит профиль служебного шума, очищает текст,
 * извлекает структурированные таблицы и собирает готовую блочную модель
 * документа. Один и тот же профиль шума применяется к тексту и таблицам,
 * чтобы watermark-и чистились единообразно.</p>
 */
@Service
public class PdfParsingPipeline {

    private final PdfReaderPort pdfReaderPort;
    private final DocumentNoiseProfileBuilder documentNoiseProfileBuilder;
    private final TextCleaner textCleaner;
    private final PdfTableParsingService pdfTableParsingService;
    private final ParsedDocumentAssembler parsedDocumentAssembler;

    public PdfParsingPipeline(
            PdfReaderPort pdfReaderPort,
            DocumentNoiseProfileBuilder documentNoiseProfileBuilder,
            TextCleaner textCleaner,
            PdfTableParsingService pdfTableParsingService,
            ParsedDocumentAssembler parsedDocumentAssembler
    ) {
        this.pdfReaderPort = Objects.requireNonNull(pdfReaderPort, "PDF reader port must not be null");
        this.documentNoiseProfileBuilder = Objects.requireNonNull(
                documentNoiseProfileBuilder,
                "Document noise profile builder must not be null"
        );
        this.textCleaner = Objects.requireNonNull(textCleaner, "Text cleaner must not be null");
        this.pdfTableParsingService = Objects.requireNonNull(
                pdfTableParsingService,
                "PDF table parsing service must not be null"
        );
        this.parsedDocumentAssembler = Objects.requireNonNull(
                parsedDocumentAssembler,
                "Parsed document assembler must not be null"
        );
    }

    /**
     * Преобразует PDF-файл в готовую блочную модель документа.
     *
     * @param content байтовое представление PDF-файла
     * @return готовый распарсенный документ
     */
    public ParsedDocument parse(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");

        ExtractedTextDocument extractedTextDocument = pdfReaderPort.read(content);
        DocumentNoiseProfile noiseProfile = documentNoiseProfileBuilder.build(extractedTextDocument);
        CleanedTextDocument cleanedTextDocument = textCleaner.cleanDocument(extractedTextDocument, noiseProfile);
        List<ParsedTable> tables = pdfTableParsingService.parseTables(content, noiseProfile);

        return parsedDocumentAssembler.assemble(cleanedTextDocument, tables);
    }
}
