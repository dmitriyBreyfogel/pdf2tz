package com.pdf2tz.backend.application.pdf.chunking.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Находит повторяющиеся верхние строки страниц, которые не должны становиться
 * заголовками разделов. Строки остаются в тексте документа.
 *
 * <p>Сравнение короткого буквенного префикса терпит небольшие OCR-ошибки в
 * логотипе или колонтитуле, не привязываясь к названию производителя.</p>
 */
final class RepeatedPageHeaderDetector {

    private static final int LINES_PER_PAGE = 2;
    private static final int PREFIX_LENGTH = 10;
    private static final int MAX_PREFIX_DIFFERENCES = 2;
    private static final int MIN_DISTINCT_PAGES = 3;

    Set<Integer> detect(List<SourceLine> lines) {
        Map<Integer, List<SourceLine>> firstLinesByPage = new LinkedHashMap<>();
        for (SourceLine line : lines) {
            List<SourceLine> pageLines = firstLinesByPage.computeIfAbsent(
                    line.pageNumber(), ignored -> new ArrayList<>()
            );
            if (pageLines.size() < LINES_PER_PAGE) {
                pageLines.add(line);
            }
        }

        Map<String, Set<Integer>> pagesByPrefix = new HashMap<>();
        Map<Integer, String> prefixByOrder = new HashMap<>();
        for (List<SourceLine> pageLines : firstLinesByPage.values()) {
            for (SourceLine line : pageLines) {
                String prefix = fingerprint(line.text());
                if (prefix != null) {
                    prefixByOrder.put(line.order(), prefix);
                    pagesByPrefix.computeIfAbsent(prefix, ignored -> new HashSet<>())
                            .add(line.pageNumber());
                }
            }
        }

        Set<String> repeatedPrefixes = new HashSet<>();
        for (String prefix : pagesByPrefix.keySet()) {
            Set<Integer> matchingPages = new HashSet<>();
            for (Map.Entry<String, Set<Integer>> other : pagesByPrefix.entrySet()) {
                if (differenceCount(prefix, other.getKey()) <= MAX_PREFIX_DIFFERENCES) {
                    matchingPages.addAll(other.getValue());
                }
            }
            if (matchingPages.size() >= MIN_DISTINCT_PAGES) {
                repeatedPrefixes.add(prefix);
            }
        }

        Set<Integer> result = new HashSet<>();
        prefixByOrder.forEach((order, prefix) -> {
            if (repeatedPrefixes.contains(prefix)) {
                result.add(order);
            }
        });
        return Set.copyOf(result);
    }

    private String fingerprint(String text) {
        String normalized = text.toUpperCase(Locale.ROOT)
                .replace('0', 'O')
                .replace('1', 'I')
                .replace('5', 'S')
                .replaceAll("[^\\p{L}]", "");
        if (normalized.length() < PREFIX_LENGTH) {
            return null;
        }
        return normalized.substring(0, PREFIX_LENGTH);
    }

    private int differenceCount(String first, String second) {
        int differences = 0;
        for (int index = 0; index < PREFIX_LENGTH; index++) {
            if (first.charAt(index) != second.charAt(index)) {
                differences++;
            }
        }
        return differences;
    }
}
