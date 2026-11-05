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

    /** Допуск на титульный лист и расхождение печатной нумерации с номером PDF-страницы. */
    private static final int MAX_CONTENTS_PAGE_DELTA = 1;
    /** Ограничения для номера и длины заголовка без подтверждения оглавлением. */
    private static final int MAX_UNCATALOGUED_SECTION_NUMBER = 99;
    private static final int MAX_UNCATALOGUED_HEADING_WORDS = 7;
    private static final int MAX_UNCATALOGUED_HEADING_LENGTH = 80;

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
            "(?iu).*(?:"
                    + "\\d"
                    + "|\\b(?:mg|ml|kg|g|мг|мл|кг|г|мм|см|л|м|"
                    + "mmhg|hpa|hz|kw|mv|psi|кпа|дб|об)\\b"
                    + "|смh2o|об\\.?%"
                    + ").*",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern NON_WORD_PATTERN = Pattern.compile("[^\\p{L}\\p{N}]+");

    List<HeadingCandidate> detect(List<SourceLine> lines) {
        return detect(lines, new ContentsRegionDetector().analyze(lines));
    }

    List<HeadingCandidate> detect(
            List<SourceLine> lines,
            ContentsRegionDetector.ContentsProfile contentsProfile
    ) {
        return detect(lines, contentsProfile, Set.of());
    }

    List<HeadingCandidate> detect(
            List<SourceLine> lines,
            ContentsRegionDetector.ContentsProfile contentsProfile,
            Set<Integer> tableCellLineOrders
    ) {
        List<HeadingCandidate> candidates = new ArrayList<>();
        Set<Integer> pageHeaderOrders = new RepeatedPageHeaderDetector().detect(lines);
        Set<Integer> pagesWithRepeatedHeader = new HashSet<>();
        Map<Integer, Integer> firstOrderByPage = new HashMap<>();
        for (SourceLine line : lines) {
            firstOrderByPage.putIfAbsent(line.pageNumber(), line.order());
            if (pageHeaderOrders.contains(line.order())) {
                pagesWithRepeatedHeader.add(line.pageNumber());
            }
        }
        boolean runningHeaderDominates = pagesWithRepeatedHeader.size() >= 3
                && pagesWithRepeatedHeader.size() * 2 >= firstOrderByPage.size();

        for (SourceLine line : lines) {
            HeadingCandidate candidate = candidate(
                    line,
                    contentsProfile.tocLineOrders().contains(line.order()),
                    contentsProfile.headingPages()
            );
            if (candidate != null
                    && tableCellLineOrders.contains(line.order())
                    && !candidate.contentsCatalogMatch()) {
                continue;
            }
            if (pageHeaderOrders.contains(line.order())
                    && (candidate == null || !candidate.contentsCatalogMatch())
                    && !line.text().trim().matches("^\\d+(?:\\.\\d+)+[.)]?\\s+\\p{L}.*")) {
                continue;
            }
            if (candidate != null
                    && !(runningHeaderDominates
                    && firstOrderByPage.get(line.pageNumber()) == line.order()
                    && candidate.scheme() == HeadingScheme.UNNUMBERED_UPPERCASE
                    && !candidate.contentsCatalogMatch())) {
                candidates.add(candidate);
            }
        }

        DocumentHeadingProfile profile = DocumentHeadingProfile.from(candidates, lines);
        Map<PageTitle, Integer> occurrences = new HashMap<>();
        for (HeadingCandidate candidate : candidates) {
            occurrences.merge(new PageTitle(
                    candidate.sourceLine().pageNumber(),
                    normalizeTitle(candidate.headingText())
            ), 1, Integer::sum);
        }
        List<HeadingCandidate> accepted = candidates.stream()
                .map(candidate -> candidate.withRepeatedOnSourcePage(
                        occurrences.get(new PageTitle(
                                candidate.sourceLine().pageNumber(),
                                normalizeTitle(candidate.headingText())
                        )) > 1
                ))
                .filter(candidate -> accepted(candidate, profile))
                .toList();
        return addUnambiguousCatalogHeadings(lines, accepted, contentsProfile);
    }

    /**
     * Пункт оглавления с единственной целевой страницей может восстановить
     * пропавший заголовок. Исходная строка страницы остаётся обычным текстом.
     */
    private List<HeadingCandidate> addUnambiguousCatalogHeadings(
            List<SourceLine> lines,
            List<HeadingCandidate> accepted,
            ContentsRegionDetector.ContentsProfile contentsProfile
    ) {
        long verifiedHeadings = accepted.stream()
                .filter(HeadingCandidate::contentsCatalogMatch)
                .count();
        if (verifiedHeadings < 2) {
            return accepted;
        }

        Set<Integer> pagesWithHeading = accepted.stream()
                .map(candidate -> candidate.sourceLine().pageNumber())
                .collect(java.util.stream.Collectors.toSet());
        Map<Integer, SourceLine> firstLineByPage = new HashMap<>();
        for (SourceLine line : lines) {
            firstLineByPage.putIfAbsent(line.pageNumber(), line);
        }

        List<HeadingCandidate> result = new ArrayList<>(accepted);
        for (Map.Entry<Integer, List<String>> entry : contentsProfile.titlesByPage().entrySet()) {
            int pageNumber = entry.getKey();
            if (entry.getValue().size() != 1 || pagesWithHeading.contains(pageNumber)) {
                continue;
            }
            SourceLine anchor = firstLineByPage.get(pageNumber);
            String title = entry.getValue().get(0);
            if (anchor == null
                    || contentsProfile.tocLineOrders().contains(anchor.order())
                    || !isReliableCatalogTitle(title)) {
                continue;
            }
            result.add(createCandidate(
                    anchor, HeadingScheme.TITLE_CASE, title,
                    List.of(), false, true
            ).asInferredFromContents());
        }
        result.sort(java.util.Comparator.comparingInt(candidate -> candidate.sourceLine().order()));
        return List.copyOf(result);
    }

    private boolean isReliableCatalogTitle(String title) {
        return looksLikeTitleCase(title)
                && title.codePointCount(0, title.length()) <= 100
                && !title.matches("(?s).*\\d{2,4}\\p{L}.*")
                && !title.matches("(?s).*\\p{Ll}[\\s\\p{Z}]+\\p{Lu}\\p{Ll}{2,}.*");
    }

    private HeadingCandidate candidate(
            SourceLine sourceLine,
            boolean tocLike,
            Map<String, Set<Integer>> contentsHeadingPages
    ) {
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
                    tocLike,
                    matchesContentsCatalog(title, sourceLine, contentsHeadingPages)
            );
        }

        Matcher romanMatcher = ROMAN_PATTERN.matcher(text);
        if (romanMatcher.matches()) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.ROMAN,
                    romanMatcher.group(2).trim(),
                    List.of(),
                    tocLike,
                    matchesContentsCatalog(romanMatcher.group(2), sourceLine, contentsHeadingPages)
            );
        }

        Matcher letterMatcher = LETTER_PATTERN.matcher(text);
        if (letterMatcher.matches()) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.LETTER,
                    letterMatcher.group(2).trim(),
                    List.of(),
                    tocLike,
                    matchesContentsCatalog(letterMatcher.group(2), sourceLine, contentsHeadingPages)
            );
        }

        if (looksLikeUppercaseHeading(text) && !FIGURE_OR_TABLE_PATTERN.matcher(text).find()) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.UNNUMBERED_UPPERCASE,
                    text,
                    List.of(),
                    tocLike,
                    matchesContentsCatalog(text, sourceLine, contentsHeadingPages)
            );
        }

        if (looksLikeCatalogTitle(text, sourceLine, contentsHeadingPages)) {
            return createCandidate(
                    sourceLine,
                    HeadingScheme.TITLE_CASE,
                    text,
                    List.of(),
                    tocLike,
                    true
            );
        }

        return null;
    }

    private HeadingCandidate createCandidate(
            SourceLine sourceLine,
            HeadingScheme scheme,
            String headingText,
            List<Integer> numbering,
            boolean tocLike,
            boolean contentsCatalogMatch
    ) {
        int wordCount = countWords(headingText);
        int visibleLength = headingText.codePointCount(0, headingText.length());
        double uppercaseRatio = uppercaseRatio(headingText);
        boolean endsWithPunctuation = headingText.matches(".*[.!?,;:]$");

        return new HeadingCandidate(
                sourceLine,
                scheme,
                headingText,
                List.copyOf(numbering),
                wordCount,
                visibleLength,
                uppercaseRatio,
                endsWithPunctuation,
                tocLike,
                contentsCatalogMatch,
                false,
                false
        );
    }

    private boolean accepted(HeadingCandidate candidate, DocumentHeadingProfile profile) {
        if (candidate.tocLike()
                || candidate.isTooLong()
                || candidate.endsWithPunctuation()
                || looksLikeValue(candidate) && !candidate.contentsCatalogMatch()) {
            return false;
        }
        if (isDiagramLabel(candidate, profile)) {
            return false;
        }
        if (isRepeatedPageHeader(candidate, profile)) {
            return false;
        }
        if (candidate.scheme() == HeadingScheme.DECIMAL) {
            return acceptedDecimal(candidate, profile);
        }
        if (candidate.scheme() == HeadingScheme.ROMAN) {
            return candidate.contentsCatalogMatch()
                    || profile.hasNearbyRomanSequence(candidate);
        }
        if (candidate.scheme() == HeadingScheme.LETTER) {
            return candidate.contentsCatalogMatch()
                    || candidate.sourceLine().lineIndex() <= 3
                    && candidate.wordCount() <= 7
                    && candidate.visibleLength() <= 80
                    && !candidate.headingText().contains(":")
                    && candidate.headingText().codePoints().noneMatch(Character::isDigit)
                    && profile.hasNearbyLetterSequence(candidate);
        }
        if (candidate.scheme() == HeadingScheme.TITLE_CASE) {
            return candidate.contentsCatalogMatch();
        }
        if (candidate.contentsCatalogMatch()) {
            return true;
        }
        if (profile.hasEstablishedNumberedHierarchy()) {
            return false;
        }
        if (candidate.sourceLine().lineIndex() > 3
                || containsShortLetterToken(candidate.headingText())) {
            return false;
        }
        return candidate.isStrongStyle()
                && profile.uppercaseCount() >= 2
                && (candidate.wordCount() >= 2 || isStandaloneSectionTitle(candidate))
                && !isRepeatedShortLabel(candidate, profile);
    }

    private boolean isDiagramLabel(
            HeadingCandidate candidate,
            DocumentHeadingProfile profile
    ) {
        return candidate.scheme() == HeadingScheme.UNNUMBERED_UPPERCASE
                && profile.markerLabels().contains(normalizeTitle(candidate.headingText()));
    }

    private boolean looksLikeValue(HeadingCandidate candidate) {
        if (candidate.scheme() != HeadingScheme.DECIMAL) {
            return false;
        }
        String text = candidate.headingText().trim();
        return VALUE_LIKE_PATTERN.matcher(text).matches()
                || text.matches("(?iu)^[a-zа-яё]{1,3}$");
    }

    private boolean acceptedDecimal(HeadingCandidate candidate, DocumentHeadingProfile profile) {
        if (candidate.contentsCatalogMatch()) {
            return true;
        }

        // Номера страниц, почтовые индексы и шаги процедуры тоже начинаются с числа.
        // Без подтверждения оглавлением принимаем только короткое название раздела.
        if (candidate.numbering().get(0) == 0
                || candidate.numbering().get(0) > MAX_UNCATALOGUED_SECTION_NUMBER
                || candidate.headingText().codePoints().filter(Character::isLetter).count() < 3
                || candidate.wordCount() > MAX_UNCATALOGUED_HEADING_WORDS
                || candidate.visibleLength() > MAX_UNCATALOGUED_HEADING_LENGTH
                || !Character.isUpperCase(candidate.headingText().codePointAt(0))) {
            return false;
        }

        int depth = candidate.numbering().size();
        if (depth >= 2) {
            return profile.decimalDepths().contains(depth)
                    && (profile.decimalCount() >= 2 || candidate.isStrongStyle());
        }
        return candidate.isStrongStyle() && profile.decimalLevelOneCount() >= 2;
    }

    private boolean looksLikeUppercaseHeading(String text) {
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters == 0 || countWords(text) > 12 || text.codePointCount(0, text.length()) > 120) {
            return false;
        }
        int firstCodePoint = text.codePointAt(0);
        if (!Character.isLetter(firstCodePoint)) {
            return false;
        }
        long uppercase = text.codePoints()
                .filter(Character::isLetter)
                .filter(Character::isUpperCase)
                .count();
        return (double) uppercase / letters >= 0.70;
    }

    private boolean looksLikeCatalogTitle(
            String text,
            SourceLine sourceLine,
            Map<String, Set<Integer>> contentsHeadingPages
    ) {
        return looksLikeTitleCase(text)
                && matchesContentsCatalog(text, sourceLine, contentsHeadingPages);
    }

    private boolean matchesContentsCatalog(
            String text,
            SourceLine sourceLine,
            Map<String, Set<Integer>> contentsHeadingPages
    ) {
        String normalizedText = normalizeTitle(text);
        if (containsMultipleCatalogTitles(
                normalizedText, sourceLine.pageNumber(), contentsHeadingPages)) {
            return false;
        }
        Set<Integer> exactPages = contentsHeadingPages.get(normalizedText);
        if (exactPages != null && matchesSourcePage(exactPages, sourceLine.pageNumber())) {
            return true;
        }
        if (sourceLine.lineIndex() > 2) {
            return false;
        }
        for (Map.Entry<String, Set<Integer>> entry : contentsHeadingPages.entrySet()) {
            String catalogTitle = entry.getKey();
            if (catalogTitle.length() < 5
                    || !(normalizedText.startsWith(catalogTitle + " ")
                    || normalizedText.endsWith(" " + catalogTitle)
                    || normalizedText.endsWith(catalogTitle))) {
                continue;
            }
            if (matchesSourcePage(entry.getValue(), sourceLine.pageNumber())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMultipleCatalogTitles(
            String text,
            int pageNumber,
            Map<String, Set<Integer>> contentsHeadingPages
    ) {
        for (int separator = text.indexOf(' '); separator >= 0;
             separator = text.indexOf(' ', separator + 1)) {
            Set<Integer> firstPages = contentsHeadingPages.get(text.substring(0, separator));
            Set<Integer> secondPages = contentsHeadingPages.get(text.substring(separator + 1));
            if (firstPages != null && secondPages != null
                    && matchesSourcePage(firstPages, pageNumber)
                    && matchesSourcePage(secondPages, pageNumber)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSourcePage(Set<Integer> expectedPages, int sourcePageNumber) {
        return expectedPages.stream().anyMatch(expectedPage ->
                Math.abs(expectedPage - sourcePageNumber) <= MAX_CONTENTS_PAGE_DELTA);
    }

    private boolean looksLikeTitleCase(String text) {
        String normalized = normalizeTitle(text);
        long letters = text.codePoints().filter(Character::isLetter).count();
        if (letters == 0
                || !Character.isLetter(text.codePointAt(0))
                || countWords(text) > 12
                || text.codePointCount(0, text.length()) > 120) {
            return false;
        }
        return !text.matches(".*[.!?,;:]$")
                && text.codePoints().anyMatch(Character::isLowerCase)
                && normalized.length() <= 120;
    }

    /** Короткие буквенные обрывки чаще являются подписями; числа могут быть частью модели изделия. */
    private boolean containsShortLetterToken(String text) {
        return NON_WORD_PATTERN.split(text.trim()).length > 1
                && java.util.Arrays.stream(NON_WORD_PATTERN.split(text.trim()))
                .anyMatch(token -> token.codePointCount(0, token.length()) <= 2
                        && token.codePoints().anyMatch(Character::isLetter));
    }

    private boolean isStandaloneSectionTitle(HeadingCandidate candidate) {
        return candidate.wordCount() == 1
                && candidate.visibleLength() >= 5
                && candidate.sourceLine().lineIndex() <= 2;
    }

    private boolean isRepeatedPageHeader(
            HeadingCandidate candidate,
            DocumentHeadingProfile profile
    ) {
        return candidate.scheme() == HeadingScheme.DECIMAL
                && candidate.numbering().size() == 1
                && candidate.numbering().get(0) == candidate.sourceLine().pageNumber()
                && profile.normalizedTextCount(candidate.headingText()) >= 2;
    }

    private boolean isRepeatedShortLabel(
            HeadingCandidate candidate,
            DocumentHeadingProfile profile
    ) {
        return candidate.wordCount() <= 3
                && profile.normalizedTextCount(candidate.headingText())
                >= 2;
    }

    private String normalizeTitle(String text) {
        return text
                .replace('\u00A0', ' ')
                .replaceAll("[\\s\\p{Z}]+", " ")
                .replaceAll(" */ *", "/")
                .trim()
                .toLowerCase(Locale.ROOT);
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

    private record PageTitle(int pageNumber, String title) {
    }

    private record DocumentHeadingProfile(
            int decimalCount,
            int decimalLevelOneCount,
            Set<Integer> decimalDepths,
            List<HeadingCandidate> romanCandidates,
            List<HeadingCandidate> letterCandidates,
            int uppercaseCount,
            Map<String, Integer> normalizedTextCounts,
            Set<String> markerLabels
    ) {

        private static DocumentHeadingProfile from(
                List<HeadingCandidate> candidates,
                List<SourceLine> lines
        ) {
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
                    candidates.stream()
                            .filter(candidate -> candidate.scheme() == HeadingScheme.ROMAN)
                            .toList(),
                    candidates.stream()
                            .filter(candidate -> candidate.scheme() == HeadingScheme.LETTER)
                            .toList(),
                    counts.getOrDefault(HeadingScheme.UNNUMBERED_UPPERCASE, 0),
                    textCounts(lines),
                    markerLabels(lines)
            );
        }

        private static Map<String, Integer> textCounts(List<SourceLine> lines) {
            Map<String, Integer> result = new HashMap<>();
            for (SourceLine line : lines) {
                String normalizedLine = normalize(line.text());
                result.merge(normalizedLine, 1, Integer::sum);

                Matcher decimalMatcher = DECIMAL_PATTERN.matcher(line.text().trim());
                if (decimalMatcher.matches()) {
                    result.merge(normalize(decimalMatcher.group(2)), 1, Integer::sum);
                }

                String withoutMarker = line.text().trim()
                        .replaceFirst("^[\\u2022\\u2013\\u2014+]+\\s*", "");
                if (!withoutMarker.equals(line.text().trim())) {
                    result.merge(normalize(withoutMarker), 1, Integer::sum);
                }
            }
            return Map.copyOf(result);
        }

        private static Set<String> markerLabels(List<SourceLine> lines) {
            return lines.stream()
                    .map(SourceLine::text)
                    .map(String::trim)
                    .filter(text -> text.matches("^[\\u2022\\u2013\\u2014+].+"))
                    .map(text -> text.replaceFirst(
                            "^[\\u2022\\u2013\\u2014+]+\\s*",
                            ""
                    ))
                    .map(DocumentHeadingProfile::normalize)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private int normalizedTextCount(String text) {
            return normalizedTextCounts.getOrDefault(normalize(text), 0);
        }

        /** Нумерованный стиль подтверждается несколькими разделами второго уровня,
         * а не одиночными числами, похожими на пункты процедуры. */
        private boolean hasEstablishedNumberedHierarchy() {
            return decimalCount >= 12 && decimalDepths.contains(2);
        }

        private boolean hasNearbyLetterSequence(HeadingCandidate candidate) {
            int marker = letterMarker(candidate);
            return letterCandidates.stream()
                    .filter(other -> Math.abs(other.sourceLine().pageNumber()
                            - candidate.sourceLine().pageNumber()) <= 2)
                    .anyMatch(other -> {
                        int otherMarker = letterMarker(other);
                        return otherMarker != marker
                                && (marker == 'A' || marker == 'А'
                                || otherMarker == 'A' || otherMarker == 'А');
                    });
        }

        /**
         * Римский маркер принимается только при соседнем последовательном номере.
         * Одиночные I. и C. часто оказываются буквенными обозначениями строк таблицы.
         */
        private boolean hasNearbyRomanSequence(HeadingCandidate candidate) {
            int number = romanNumber(candidate);
            return romanCandidates.stream()
                    .filter(other -> other != candidate)
                    .filter(other -> Math.abs(other.sourceLine().pageNumber()
                            - candidate.sourceLine().pageNumber()) <= 2)
                    .anyMatch(other -> Math.abs(romanNumber(other) - number) == 1);
        }

        private static int romanNumber(HeadingCandidate candidate) {
            Matcher matcher = ROMAN_PATTERN.matcher(candidate.sourceLine().text().trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Roman candidate has no numeral");
            }
            String marker = matcher.group(1).toUpperCase(Locale.ROOT);
            int result = 0;
            int previous = 0;
            for (int index = marker.length() - 1; index >= 0; index--) {
                int current = switch (marker.charAt(index)) {
                    case 'I' -> 1;
                    case 'V' -> 5;
                    case 'X' -> 10;
                    case 'L' -> 50;
                    case 'C' -> 100;
                    case 'D' -> 500;
                    case 'M' -> 1000;
                    default -> throw new IllegalArgumentException("Invalid Roman numeral");
                };
                result += current < previous ? -current : current;
                previous = current;
            }
            return result;
        }

        private static int letterMarker(HeadingCandidate candidate) {
            return Character.toUpperCase(
                    candidate.sourceLine().text().trim().codePointAt(0)
            );
        }

        private static String normalize(String text) {
            return text
                    .replace('\u00A0', ' ')
                    .replaceAll("[\\s\\p{Z}]+", " ")
                    .replaceAll(" */ *", "/")
                    .trim()
                    .toLowerCase(Locale.ROOT);
        }
    }
}
