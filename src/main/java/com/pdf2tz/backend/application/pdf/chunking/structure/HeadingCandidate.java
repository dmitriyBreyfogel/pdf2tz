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
 * @param contentsCatalogMatch признак подтверждения заголовка оглавлением
 * @param repeatedOnSourcePage признак повторения того же заголовка на странице
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
        boolean tocLike,
        boolean contentsCatalogMatch,
        boolean repeatedOnSourcePage
) {

    HeadingCandidate withRepeatedOnSourcePage(boolean repeated) {
        return new HeadingCandidate(
                sourceLine,
                scheme,
                headingText,
                numbering,
                wordCount,
                visibleLength,
                uppercaseRatio,
                endsWithPunctuation,
                tocLike,
                contentsCatalogMatch,
                repeated
        );
    }

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
