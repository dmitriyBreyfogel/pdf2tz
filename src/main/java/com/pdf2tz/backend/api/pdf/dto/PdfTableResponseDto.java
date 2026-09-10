package com.pdf2tz.backend.api.pdf.dto;

import java.util.List;

/**
 * DTO извлечённой таблицы PDF-документа.
 *
 * @param tableNumber порядковый номер таблицы в результате
 * @param startPageNumber номер страницы, на которой таблица начинается
 * @param endPageNumber номер страницы, на которой таблица заканчивается
 * @param multiPage признак таблицы, склеенной из нескольких страниц
 * @param columnCount количество колонок таблицы
 * @param fragments фрагменты таблицы с координатами исходных областей
 * @param rows строки таблицы в порядке чтения
 */
public record PdfTableResponseDto(
        int tableNumber,
        int startPageNumber,
        int endPageNumber,
        boolean multiPage,
        int columnCount,
        List<PdfTableFragmentResponseDto> fragments,
        List<List<String>> rows
) { }
