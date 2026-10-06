package com.pdf2tz.backend.application.pdf.chunking.text;

import com.pdf2tz.backend.application.ports.TextBoundaryPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Выбирает максимальный lossless prefix текста под динамическим payload budget.
 *
 * <p>Порядок fallback-границ: абзацы, предложения, словесные границы,
 * затем границы Unicode code point.
 * Метод не знает о section path и serializer: caller передаёт predicate,
 * который проверяет полный prospective content.</p>
 */
@Component
public class TextSegmentSplitter {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n{2,}");

    private final TextBoundaryPort textBoundaryPort;

    public TextSegmentSplitter(TextBoundaryPort textBoundaryPort) {
        this.textBoundaryPort = Objects.requireNonNull(textBoundaryPort, "Text boundary port must not be null");
    }

    /**
     * Выделяет самый длинный prefix, для которого predicate возвращает true.
     *
     * @param text исходный текст
     * @param fits проверка полного payload candidate
     * @return prefix и остаток; если даже один token не помещается, бросает exception
     */
    public TextSplitResult splitFirst(
            String text,
            Predicate<String> fits
    ) {
        Objects.requireNonNull(text, "Text must not be null");
        Objects.requireNonNull(fits, "Fits predicate must not be null");

        if (text.isBlank()) {
            return new TextSplitResult("", "");
        }
        if (fits.test(text)) {
            return new TextSplitResult(text, "");
        }

        TextSplitResult paragraphResult = byBoundaries(text, paragraphEndOffsets(text), fits);
        if (!paragraphResult.acceptedPrefix().isBlank()) {
            return paragraphResult;
        }

        TextSplitResult sentenceResult = byBoundaries(text, textBoundaryPort.sentenceEndOffsets(text), fits);
        if (!sentenceResult.acceptedPrefix().isBlank()) {
            return sentenceResult;
        }

        TextSplitResult wordResult = byBoundaries(text, textBoundaryPort.wordEndOffsets(text), fits);
        if (!wordResult.acceptedPrefix().isBlank()) {
            return wordResult;
        }

        return byCodePoints(text, fits);
    }

    private List<Integer> paragraphEndOffsets(String text) {
        Matcher matcher = PARAGRAPH_BREAK.matcher(text);
        List<Integer> offsets = new ArrayList<>();
        while (matcher.find()) {
            offsets.add(matcher.end());
        }
        return offsets;
    }

    private TextSplitResult byBoundaries(
            String text,
            List<Integer> offsets,
            Predicate<String> fits
    ) {
        int bestEnd = 0;
        for (Integer end : offsets) {
            if (end == null || end <= bestEnd || end > text.length()) {
                continue;
            }
            String candidate = text.substring(0, end);
            if (!fits.test(candidate)) {
                break;
            }
            bestEnd = end;
        }

        return splitAt(text, bestEnd);
    }

    private TextSplitResult byCodePoints(
            String text,
            Predicate<String> fits
    ) {
        int low = 1;
        int high = text.codePointCount(0, text.length());
        int bestEnd = 0;

        while (low <= high) {
            int codePoints = low + (high - low) / 2;
            int end = text.offsetByCodePoints(0, codePoints);
            if (fits.test(text.substring(0, end))) {
                bestEnd = end;
                low = codePoints + 1;
            } else {
                high = codePoints - 1;
            }
        }

        return splitAt(text, bestEnd);
    }

    private TextSplitResult splitAt(String text, int end) {
        if (end <= 0) {
            return new TextSplitResult("", text);
        }
        return new TextSplitResult(text.substring(0, end), text.substring(end));
    }
}
