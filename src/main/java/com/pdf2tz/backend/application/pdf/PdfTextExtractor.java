package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PdfTextExtractor {
    private final PdfReaderPort pdfReaderPort;

    public PdfTextExtractor(PdfReaderPort pdfReaderPort) {
        this.pdfReaderPort = pdfReaderPort;
    }

    /**
     * Извлекает текстовое содержимое PDF-документа.
     *
     * @param content байтовое представление PDF-файла
     * @return извлечённое содержимое документа
     */
    public ExtractedDocument extract(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");
        return pdfReaderPort.read(content);
    }
}
