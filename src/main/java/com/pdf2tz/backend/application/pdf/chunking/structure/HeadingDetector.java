package com.pdf2tz.backend.application.pdf.chunking.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Двухпроходный консервативный детектор заголовков.
 *
 * <p>Первый проход находит только кандидатов, второй оценивает их на фоне
 * конкретного документа. Это снижает риск принять единицу измерения, шаг
 * списка или подпись к рисунку за раздел.</p>
 */
final class HeadingDetector {

    private static final Pattern DECIMAL_PATTERN = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)*)(?:[.)]?\\s+)(\\p{L}.*)$",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern ROMAN_PATTERN = Pattern.compile(
            "^\\s*([IVXLCDM]+)[.)]\\s+(\\p{L}.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern LETTER_PATTERN = Pattern.compile(
            "^\\s*([A-ZА-ЯЁ])(?:[.)])\\s+(\\p{L}.*)$",
            Pattern.UNICODE_CASE
    );
    private static final Pattern ENUMERATION_PATTERN = Pattern.compile(
            "^\\s*\\d+[)]\\s+"
    );
    private static final Pattern FIGURE_OR_TABLE_PATTERN = Pattern.compile(
            "^\\s*(?:рисунок|рис\\.|figure|таблица|табл\\.|table)\\s+\\d+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern VALUE_LIKE_PATTERN = Pattern.compile(
            "(?i)^(?:[a-z\\p{L}]{1,10}/[a-z\\p{L}]{1,10}|.*\\b(?:mg|ml|kg|mmhg|hz|kw|mv|v|%)\\b.*)$",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");

    List<HeadingCandidate> detect(List<SourceLine> lines) {
        List<HeadingCandidate> candidates = new ArrayList<>();
        Set<Integer> tocLines = new ContentsRegionDetector().detect(lines);

        for (SourceLine line : lines) {
            HeadingCandidate candidate = candidate(line, tocLines.contains(line.order()));
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        DocumentHeadingProfile profile = DocumentHeadingProfile.from(candidates);
        return candidates.stream()
                .filter(candidate -> accepted(candidate, profile))
                .toList();
    }

    private HeadingCandidate candidate(SourceLine sourceLine, boolean tocLike) {
        String text = sourceLine.text().trim();
        if (text.isBlank()) {
            return null;
        }

        Matcher decimalMatcher = DECIMAL_PATTERN.matcher(text);
        if (decimalMatcher.matches() && !ENUMERATION_PATTERN.matcher(text).find()) {
            String number = decimalMatcher.group(1);
            String title = decimalMatcher.group(2).trim();
            return createCandidate(
                    sourceLine,
                    HeadingScheme.DECIMAL,
                    title,
                    parseDecimal(number),
                    tocLike
            );
        }

        Matcher romanMatcher = ROMAN_PATTERN.matcher(text);
        if (romanMatcher.matches()) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.ROMAN,
                    romanMatcher.group(2).trim(),
                    List.of(),
                    tocLike
            );
        }

        Matcher letterMatcher = LETTER_PATTERN.matcher(text);
        if (letterMatcher.matches()) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.LETTER,
                    letterMatcher.group(2).trim(),
                    List.of(),
                    tocLike
            );
        }

        if (looksLikeUppercaseHeading(text) && !FIGURE_OR_TABLE_PATTERN.matcher(text).find()) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.UNNUMBERED_UPPERCASE,
                    text,
                    List.of(),
                    tocLike
            );
        }

        return null;
    }

    private HeadingCandidate createCandidate(
            SourceLine sourceLine,
            HeadingScheme scheme,
            String headingText,
            List<Integer> numbering,
            boolean tocLike
    ) {
        int wordCount = countWords(headingText);
        int visibleLength = headingText.codePointCount(0, headingText.length());
        double uppercaseRatio = uppercaseRatio(headingText);
        boolean endsWithPunctuation = headingText.codePoints()
                .filter(Character::isWhitespace)
                .findAny()
                .isPresent()
                && headingText.matches(".*[.!?,;:]$");

        return new HeadingCandidate(
                sourceLine,
                scheme,
                headingText,
                List.copyOf(numbering),
                wordCount,
                visibleLength,
                uppercaseRatio,
                endsWithPunctuation,
                tocLike
        );
    }

    private boolean accepted(HeadingCandidate candidate, DocumentHeadingProfile profile) {
        if (candidate.tocLike()
                || candidate.isTooLong()
                || candidate.endsWithPunctuation()
                || looksLikeValue(candidate)) {
            return false;
        }
        if (candidate.scheme() == HeadingScheme.DECIMAL) {
            return acceptedDecimal(candidate, profile);
        }
        if (candidate.scheme() == HeadingScheme.ROMAN) {
            return profile.romanCount() >= 2;
        }
        if (candidate.scheme() == HeadingScheme.LETTER) {
            return profile.letterCount() >= 2;
        }
        return candidate.isStrongStyle() && profile.uppercaseCount() >= 2;
    }

    private boolean looksLikeValue(HeadingCandidate candidate) {
        return candidate.scheme() == HeadingScheme.DECIMAL
                && VALUE_LIKE_PATTERN.matcher(candidate.headingText()).matches();
    }

    private boolean acceptedDecimal(HeadingCandidate candidate, DocumentHeadingProfile profile) {
        int depth = candidate.numbering().size();
        if (depth >= 2) {
            return profile.decimalDepths().contains(depth)
                    && (profile.decimalCount() >= 2 || candidate.isStrongStyle());
        }
        return profile.decimalLevelOneCount() >= 2 || candidate.isStrongStyle();
    }

    private boolean looksLikeUppercaseHeading(String text) {
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters == 0 || countWords(text) > 12 || text.codePointCount(0, text.length()) > 120) {
            return false;
        }
        long uppercase = text.codePoints()
                .filter(Character::isLetter)
                .filter(Character::isUpperCase)
                .count();
        return (double) uppercase / letters >= 0.70;
    }

    private List<Integer> parseDecimal(String number) {
        return List.of(number.split("\\."))
                .stream()
                .map(Integer::parseInt)
                .toList();
    }

    private int countWords(String text) {
        return NON_WORD_PATTERN.split(text.trim()).length;
    }

    private double uppercaseRatio(String text) {
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters == 0) {
            return 0;
        }
        long uppercase = text.codePoints()
                .filter(Character::isLetter)
                .filter(Character::isUpperCase)
                .count();
        return (double) uppercase / letters;
    }

    private record DocumentHeadingProfile(
            int decimalCount,
            int decimalLevelOneCount,
            Set<Integer> decimalDepths,
            int romanCount,
            int letterCount,
            int uppercaseCount
    ) {

        private static DocumentHeadingProfile from(List<HeadingCandidate> candidates) {
            Map<HeadingScheme, Integer> counts = new HashMap<>();
            Set<Integer> decimalDepths = new HashSet<>();
            int decimalLevelOneCount = 0;

            for (HeadingCandidate candidate : candidates) {
                counts.merge(candidate.scheme(), 1, Integer::sum);
                if (candidate.scheme() == HeadingScheme.DECIMAL) {
                    decimalDepths.add(candidate.numbering().size());
                    if (candidate.numbering().size() == 1) {
                        decimalLevelOneCount++;
                    }
                }
            }

            return new DocumentHeadingProfile(
                    counts.getOrDefault(HeadingScheme.DECIMAL, 0),
                    decimalLevelOneCount,
                    Set.copyOf(decimalDepths),
                    counts.getOrDefault(HeadingScheme.ROMAN, 0),
                    counts.getOrDefault(HeadingScheme.LETTER, 0),
                    counts.getOrDefault(HeadingScheme.UNNUMBERED_UPPERCASE, 0)
            );
        }
    }
}
