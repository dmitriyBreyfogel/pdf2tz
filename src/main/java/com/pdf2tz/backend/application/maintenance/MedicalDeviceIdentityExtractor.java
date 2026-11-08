package com.pdf2tz.backend.application.maintenance;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts one conservative search phrase from the title area of an instruction.
 *
 * <p>This component intentionally does not attempt to reconstruct a medical-device
 * name with an LLM. Its only purpose is to provide a sufficiently specific phrase
 * for one registry request. An uncertain phrase results in no external request.</p>
 */
@Component
public class MedicalDeviceIdentityExtractor {
    private static final int TITLE_TEXT_LIMIT = 12_000;
    private static final Pattern TRADEMARK = Pattern.compile("[™®]");
    private static final Pattern MODEL_WITH_NUMBER = Pattern.compile(
            "(?iu)\\b(?:[a-zа-я]{3,}\\s+)?[a-zа-я]{1,12}[- ]?\\d{1,6}(?:[-/]?[a-zа-я0-9]+)*\\b"
    );
    private static final Pattern UPPERCASE_MODEL = Pattern.compile(
            "\\b[A-Z]{3,}(?:\\s+[A-Z]{2,}){1,2}\\b"
    );
    private static final Pattern LATIN_BRAND_MODEL = Pattern.compile(
            "\\b[A-Z][a-z]{3,}(?:\\s+[A-Z][a-z]{3,}){1,2}\\b"
    );
    private static final Pattern MODEL_AFTER_COMMA = Pattern.compile("(?<=[,«])\\s*[A-Za-z]{5,}\\b(?!\\s+\\d)");

    public Optional<String> extractQuery(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return Optional.empty();
        }
        String titleText = sourceText.substring(0, Math.min(sourceText.length(), TITLE_TEXT_LIMIT));
        Map<String, Integer> candidates = new LinkedHashMap<>();
        collect(candidates, MODEL_WITH_NUMBER, titleText, 3);
        collect(candidates, MODEL_AFTER_COMMA, titleText, 20);
        collect(candidates, LATIN_BRAND_MODEL, titleText, 4);
        collect(candidates, UPPERCASE_MODEL, titleText, 1);

        return candidates.entrySet().stream()
                .filter(entry -> entry.getKey().length() >= 5)
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getKey);
    }

    private void collect(Map<String, Integer> candidates, Pattern pattern, String source, int baseScore) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String candidate = normalize(matcher.group());
            if (candidate.length() < 5 || isNoise(candidate)) {
                continue;
            }
            int titleBonus = Math.max(0, 1_000 - matcher.start()) / 100;
            candidates.merge(candidate, baseScore + titleBonus, Integer::sum);
        }
    }

    private String normalize(String candidate) {
        return TRADEMARK.matcher(candidate)
                .replaceAll("")
                .replaceAll("(?i)\\bTM\\b", "")
                .replaceAll("(?i)TM(?=\\s|$)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isNoise(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.matches("(?:page|seite|лист|page) \\d+")
                || normalized.matches("(?:iso|iec|en) ?\\d+.*")
                || normalized.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}");
    }
}
