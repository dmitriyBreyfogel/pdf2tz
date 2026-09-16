package com.pdf2tz.backend.api.pdf.dto;

/**
 * DTO фрагмента таблицы на одной странице PDF-документа.
 *
 * @param pageNumber номер страницы, на которой расположен фрагмент
 * @param area координаты области фрагмента на странице
 * @param rowCount количество строк во фрагменте
 */
public record PdfTableFragmentResponseDto(
        int pageNumber,
        PdfTableAreaResponseDto area,
        int rowCount
) { }
