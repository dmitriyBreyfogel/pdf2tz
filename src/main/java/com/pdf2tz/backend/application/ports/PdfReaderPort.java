package com.pdf2tz.backend.application.ports;

import com.pdf2tz.backend.application.pdf.model.ExtractedTextDocument;

public interface PdfReaderPort {

    /**
     * Чтение текстового содержимого PDF-файла.
     *
     * @param content байтовое представление PDF-файла
     * @return извлечённое содержимое документа
     */
    ExtractedTextDocument read(byte[] content);
}
