package com.pdf2tz.backend.application.pdf.chunking.structure;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Находит плотные участки, похожие на оглавление.
 *
 * <p>Оглавление не удаляется и не переписывается. Его строки лишь не должны
 * менять текущую иерархию документа.</p>
 */
final class ContentsRegionDetector {

    private static final int MIN_REGION_LINES = 4;
    private static final int WINDOW_SIZE = 6;
    private static final Pattern DOT_LEADER = Pattern.compile("(?:\\.{2,}|…{2,})");
    private static final Pattern TRAILING_PAGE_NUMBER = Pattern.compile("\\s+\\d{1,4}\\s*$");

    Set<Integer> detect(List<SourceLine> lines) {
        Set<Integer> result = new HashSet<>();

        for (int start = 0; start < lines.size(); start++) {
            int end = Math.min(lines.size(), start + WINDOW_SIZE);
            List<SourceLine> window = lines.subList(start, end);
            long tocLikeCount = window.stream()
                    .filter(this::looksLikeContentsEntry)
                    .count();

            if (window.size() >= MIN_REGION_LINES && tocLikeCount >= MIN_REGION_LINES) {
                for (int index = start; index < end; index++) {
                    if (looksLikeContentsEntry(lines.get(index))) {
                        result.add(lines.get(index).order());
                    }
                }
            }
        }

        return Set.copyOf(result);
    }

    private boolean looksLikeContentsEntry(SourceLine line) {
        String text = line.text().trim();
        return DOT_LEADER.matcher(text).find()
                || TRAILING_PAGE_NUMBER.matcher(text).find()
                        && text.codePoints().filter(Character::isLetter).count() >= 3;
    }
}
