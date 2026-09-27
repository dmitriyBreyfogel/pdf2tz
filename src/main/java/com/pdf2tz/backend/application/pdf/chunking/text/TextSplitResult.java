package com.pdf2tz.backend.application.pdf.chunking.text;

/**
 * Результат выделения максимального prefix, помещающегося в текущий бюджет.
 *
 * @param acceptedPrefix принятый prefix исходного текста
 * @param remainder остаток исходного текста
 */
public record TextSplitResult(
        String acceptedPrefix,
        String remainder
) {
}
