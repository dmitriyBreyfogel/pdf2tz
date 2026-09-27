package com.pdf2tz.backend.application.pdf.chunking.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Находит плотные участки, похожие на оглавление.
 *
 * <p>Оглавление не удаляется и не переписывается. Его строки лишь не должны
 * менять текущую иерархию документа.</p>
 */
final class ContentsRegionDetector {

    /** Минимум похожих на пункты оглавления строк на одной странице. */
    private static final int MIN_REGION_LINES = 4;
    /** Две строки с отточиями подтверждают оглавление даже без его заголовка. */
    private static final int MIN_DOT_LEADER_LINES = 2;
    private static final Pattern DOT_LEADER = Pattern.compile("(?:\\.{2,}|…{2,})");
    private static final Pattern CONTENTS_TITLE = Pattern.compile(
            "(?iu)^(?:содержание|оглавление|contents|table of contents|inhalt)$"
    );
    private static final Pattern TRAILING_PAGE_NUMBER = Pattern.compile("\\s+\\d{1,4}\\s*$");
    private static final Pattern LEADING_NUMBERING = Pattern.compile(
            "^\\s*(?:(?:\\d+(?:\\.\\d+)*)|(?:[IVXLCDM]+)|(?:[A-ZА-ЯЁ]))[.)]?\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern PAGE_NUMBER_AFTER_LEADER = Pattern.compile("^\\s*\\d{1,4}");

    ContentsProfile analyze(List<SourceLine> lines) {
        Set<Integer> tocLineOrders = new HashSet<>();
        Map<String, Set<Integer>> headingPages = new LinkedHashMap<>();
        Map<Integer, List<SourceLine>> linesByPage = new LinkedHashMap<>();

        for (SourceLine line : lines) {
            linesByPage.computeIfAbsent(line.pageNumber(), ignored -> new ArrayList<>()).add(line);
        }

        for (List<SourceLine> pageLines : linesByPage.values()) {
            long tocLikeCount = pageLines.stream()
                    .filter(this::looksLikeContentsEntry)
                    .count();
            long dotLeaderCount = pageLines.stream()
                    .filter(line -> DOT_LEADER.matcher(line.text()).find())
                    .count();
            boolean hasContentsTitle = pageLines.stream()
                    .limit(5)
                    .anyMatch(line -> CONTENTS_TITLE.matcher(line.text().trim()).matches());
            if (tocLikeCount < MIN_REGION_LINES
                    || dotLeaderCount < MIN_DOT_LEADER_LINES && !hasContentsTitle) {
                continue;
            }

            pageLines.stream()
                    .filter(this::looksLikeContentsEntry)
                    .forEach(line -> {
                        tocLineOrders.add(line.order());
                        extractHeadingTitles(line.text(), headingPages);
                    });
        }

        return new ContentsProfile(
                Set.copyOf(tocLineOrders),
                headingPages.entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> Set.copyOf(entry.getValue())
                        ))
        );
    }

    private boolean looksLikeContentsEntry(SourceLine line) {
        String text = line.text().trim();
        return DOT_LEADER.matcher(text).find()
                || TRAILING_PAGE_NUMBER.matcher(text).find()
                        && text.codePoints().filter(Character::isLetter).count() >= 3;
    }

    private void extractHeadingTitles(
            String text,
            Map<String, Set<Integer>> headingPages
    ) {
        String remainder = text;
        while (!remainder.isBlank()) {
            var matcher = DOT_LEADER.matcher(remainder);
            if (!matcher.find()) {
                break;
            }

            String title = remainder.substring(0, matcher.start());
            String afterLeader = remainder.substring(matcher.end());
            var pageMatcher = PAGE_NUMBER_AFTER_LEADER.matcher(afterLeader);
            if (!pageMatcher.find()) {
                break;
            }

            String normalizedTitle = normalizeTitle(title);
            if (normalizedTitle.codePoints().filter(Character::isLetter).count() >= 3) {
                headingPages.computeIfAbsent(normalizedTitle, ignored -> new HashSet<>())
                        .add(Integer.parseInt(pageMatcher.group().trim()));
            }
            remainder = afterLeader.substring(pageMatcher.end());
        }
    }

    private String normalizeTitle(String title) {
        return LEADING_NUMBERING.matcher(title.replace('\u00A0', ' '))
                .replaceFirst("")
                .replaceAll("[.\\s]+$", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    record ContentsProfile(
            Set<Integer> tocLineOrders,
            Map<String, Set<Integer>> headingPages
    ) {
    }
}
