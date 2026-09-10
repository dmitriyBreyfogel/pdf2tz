package com.pdf2tz.backend.api.pdf.dto;

/**
 * DTO области табличного фрагмента на странице PDF-документа.
 *
 * @param top верхняя граница области
 * @param left левая граница области
 * @param bottom нижняя граница области
 * @param right правая граница области
 * @param width ширина области
 * @param height высота области
 */
public record PdfTableAreaResponseDto(
        double top,
        double left,
        double bottom,
        double right,
        double width,
        double height
) { }
