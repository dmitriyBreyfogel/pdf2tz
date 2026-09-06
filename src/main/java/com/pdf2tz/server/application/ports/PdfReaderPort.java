package com.pdf2tz.server.application.ports;

public interface PdfReaderPort {

    /**
     * Чтение текстового содержимого PDF-файла.
     *
     * @param content байтовое представление PDF-файла
     * @return извлечённый текст
     */
    String read(byte[] content);
}
