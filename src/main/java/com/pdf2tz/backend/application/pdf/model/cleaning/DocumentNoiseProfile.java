package com.pdf2tz.backend.application.pdf.model.cleaning;

import java.util.Set;

/**
 * Профиль служебного шума, найденного в конкретном PDF-документе.
 *
 * <p>К служебному шуму относятся водяные знаки, колонтитулы и повторяющиеся
 * технические фрагменты, которые не являются содержанием инструкции.</p>
 *
 * @param lineNoise строки, которые нужно удалять целиком
 * @param inlineNoise фрагменты, которые нужно удалять внутри строк и ячеек
 */
public record DocumentNoiseProfile(
        Set<String> lineNoise,
        Set<String> inlineNoise
) {

    public DocumentNoiseProfile {
        lineNoise = Set.copyOf(lineNoise);
        inlineNoise = Set.copyOf(inlineNoise);
    }
}
