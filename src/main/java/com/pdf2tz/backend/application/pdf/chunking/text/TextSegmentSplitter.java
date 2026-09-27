package com.pdf2tz.backend.application.pdf.chunking.text;

import com.pdf2tz.backend.application.ports.LlmTokenizerPort;
import com.pdf2tz.backend.application.ports.TextBoundaryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Выбирает максимальный lossless prefix текста под динамическим payload budget.
 *
 * <p>Порядок fallback-границ: предложения, словесные границы, token-level.
 * Метод не знает о section path и serializer: caller передаёт predicate,
 * который проверяет полный prospective content.</p>
 */
@Component
public class TextSegmentSplitter {

    private final TextBoundaryPort textBoundaryPort;
    private final LlmTokenizerPort tokenizerPort;

    public TextSegmentSplitter(
            TextBoundaryPort textBoundaryPort,
            LlmTokenizerPort tokenizerPort
    ) {
        this.textBoundaryPort = Objects.requireNonNull(textBoundaryPort, "Text boundary port must not be null");
        this.tokenizerPort = Objects.requireNonNull(tokenizerPort, "Tokenizer port must not be null");
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

        TextSplitResult sentenceResult = byBoundaries(text, textBoundaryPort.sentenceEndOffsets(text), fits);
        if (!sentenceResult.acceptedPrefix().isBlank()) {
            return sentenceResult;
        }

        TextSplitResult wordResult = byBoundaries(text, textBoundaryPort.wordEndOffsets(text), fits);
        if (!wordResult.acceptedPrefix().isBlank()) {
            return wordResult;
        }

        return byTokens(text, fits);
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

    private TextSplitResult byTokens(
            String text,
            Predicate<String> fits
    ) {
        int tokenCount = tokenizerPort.countTokens(text);
        int low = 1;
        int high = Math.max(1, tokenCount);
        String best = "";

        while (low <= high) {
            int candidateLimit = low + (high - low) / 2;
            List<String> tokenParts = tokenizerPort.splitByTokenLimit(text, candidateLimit);
            String candidate = tokenParts.isEmpty() ? "" : tokenParts.get(0);
            if (!candidate.isEmpty() && fits.test(candidate)) {
                best = candidate;
                low = candidateLimit + 1;
            } else {
                high = candidateLimit - 1;
            }
        }

        if (best.isBlank()) {
            return new TextSplitResult("", text);
        }
        return splitAt(text, best.length());
    }

    private TextSplitResult splitAt(String text, int end) {
        if (end <= 0) {
            return new TextSplitResult("", text);
        }
        return new TextSplitResult(text.substring(0, end), text.substring(end));
    }
}
