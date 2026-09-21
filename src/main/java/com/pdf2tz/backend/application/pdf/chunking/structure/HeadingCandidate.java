package com.pdf2tz.backend.application.pdf.chunking.structure;

import java.util.List;

/**
 * Внутренний результат первого прохода детектора заголовков.
 *
 * @param sourceLine исходная строка
 * @param scheme схема кандидата
 * @param headingText текст без префикса нумерации
 * @param numbering decimal-компоненты или пустой список
 * @param wordCount количество слов
 * @param visibleLength длина видимого текста
 * @param uppercaseRatio доля uppercase среди букв
 * @param endsWithPunctuation признак завершающей пунктуации
 * @param tocLike признак строки, похожей на элемент содержания
 */
record HeadingCandidate(
        SourceLine sourceLine,
        HeadingScheme scheme,
        String headingText,
        List<Integer> numbering,
        int wordCount,
        int visibleLength,
        double uppercaseRatio,
        boolean endsWithPunctuation,
        boolean tocLike
) {

    boolean isStrongStyle() {
        return uppercaseRatio >= 0.70
                && wordCount >= 1
                && wordCount <= 12
                && visibleLength <= 120
                && !endsWithPunctuation;
    }

    boolean isTooLong() {
        return visibleLength > 160 || wordCount > 20;
    }
}
