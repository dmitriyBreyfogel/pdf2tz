package com.pdf2tz.backend.application.pdf.retrieval;

import com.pdf2tz.backend.application.pdf.model.chunk.DocumentChunk;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ранжирует чанки одной инструкции по словам запроса без внешнего поискового сервиса.
 * BM25 учитывает частоту слова, длину чанка и распространённость слова в документе.
 * Для разных грамматических форм применяется ограниченное сравнение триграмм;
 * оно не заменяет семантический поиск и не расширяет запрос синонимами.
 * Переносы слов из PDF склеиваются только для сопоставления. Чанки из одних
 * заголовков остаются доступными как резерв, но идут после чанков с содержимым.
 */
@Component
public class LexicalChunkRanker {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");
    /** Склеивает переносы при поисковой нормализации, не изменяя текст источника. */
    private static final Pattern BROKEN_WORD = Pattern.compile(
            "(\\p{L})-(?:[ \\t]+|[ \\t]*\\R[ \\t]*)(\\p{Ll})");
    /** Формат префикса раздела задаёт DocumentChunkSerializer. */
    private static final Pattern HEADING_LINE = Pattern.compile("#{1,6} .+");
    /** Распространённые параметры BM25: насыщение частоты и нормализация длины. */
    private static final double K1 = 1.2;
    private static final double LENGTH_NORMALIZATION = 0.75;
    /** Короткие слова слишком часто совпадают случайно, поэтому их сравниваем только точно. */
    private static final int MIN_FUZZY_LENGTH = 5;
    /** Общий префикс и близкая длина ограничивают сравнение формами похожего слова. */
    private static final int FUZZY_PREFIX_LENGTH = 3;
    private static final int MAX_FUZZY_LENGTH_DELTA = 3;
    /** Минимальное сходство наборов триграмм; похожие темы с общим началом не должны совпадать. */
    private static final double MIN_DICE_SIMILARITY = 0.8;
    private static final double FUZZY_WEIGHT = 0.5;

    /**
     * Возвращает только чанки с текстовым совпадением: сначала содержательные,
     * затем чистые заголовки; внутри групп — по убыванию BM25. Равные результаты
     * упорядочиваются по исходному номеру чанка.
     */
    public List<RankedChunk> rank(String prompt, List<DocumentChunk> chunks) {
        Objects.requireNonNull(prompt, "Prompt must not be null");
        Objects.requireNonNull(chunks, "Chunks must not be null");
        List<String> queryTerms = List.copyOf(new LinkedHashSet<>(terms(prompt)));
        if (queryTerms.isEmpty()) {
            throw AppException.build(ErrorCode.INVALID_PROMPT,
                    "Запрос должен содержать слова или числа для поиска");
        }
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<IndexedChunk> index = chunks.stream().map(this::index).toList();
        double averageLength = index.stream().mapToInt(IndexedChunk::length).average().orElse(1);
        double[] scores = new double[index.size()];
        int[] matchedTerms = new int[index.size()];
        Map<String, Set<String>> gramCache = new HashMap<>();

        for (String queryTerm : queryTerms) {
            List<TermMatch> matches = index.stream()
                    .map(chunk -> match(queryTerm, chunk, gramCache))
                    .toList();
            long documentFrequency = matches.stream().filter(TermMatch::present).count();
            if (documentFrequency == 0) {
                continue;
            }
            double idf = Math.log1p((index.size() - documentFrequency + 0.5)
                    / (documentFrequency + 0.5));
            for (int i = 0; i < index.size(); i++) {
                TermMatch match = matches.get(i);
                if (!match.present()) {
                    continue;
                }
                double frequency = match.frequency() * match.quality();
                double lengthFactor = K1 * (1 - LENGTH_NORMALIZATION
                        + LENGTH_NORMALIZATION * index.get(i).length() / averageLength);
                scores[i] += idf * frequency * (K1 + 1) / (frequency + lengthFactor);
                matchedTerms[i]++;
            }
        }

        List<RankedChunk> ranked = new ArrayList<>();
        for (int i = 0; i < index.size(); i++) {
            if (scores[i] > 0) {
                double coverage = (double) matchedTerms[i] / queryTerms.size();
                ranked.add(new RankedChunk(index.get(i).chunk(),
                        scores[i] * (0.5 + 0.5 * coverage)));
            }
        }
        ranked.sort(Comparator.comparing((RankedChunk result) -> headingOnly(result.chunk()))
                .thenComparing(Comparator.comparingDouble(RankedChunk::score).reversed())
                .thenComparingInt(candidate -> candidate.chunk().chunkNumber()));
        return List.copyOf(ranked);
    }

    /** Проверяет, что запрос содержит хотя бы один индексируемый термин. */
    public void validatePrompt(String prompt) {
        Objects.requireNonNull(prompt, "Prompt must not be null");
        if (terms(prompt).isEmpty()) {
            throw AppException.build(ErrorCode.INVALID_PROMPT,
                    "Запрос должен содержать слова или числа для поиска");
        }
    }

    private IndexedChunk index(DocumentChunk chunk) {
        Map<String, Integer> frequencies = new HashMap<>();
        for (String term : terms(chunk.content())) {
            frequencies.merge(term, 1, Integer::sum);
        }
        return new IndexedChunk(chunk, Map.copyOf(frequencies),
                Math.max(1, frequencies.values().stream().mapToInt(Integer::intValue).sum()));
    }

    /** Чистый заголовок уступает фрагменту с телом, но остаётся доступным как резерв. */
    private boolean headingOnly(DocumentChunk chunk) {
        return !chunk.sectionPath().isRoot()
                && chunk.content().lines().allMatch(line -> HEADING_LINE.matcher(line).matches());
    }

    private TermMatch match(String queryTerm, IndexedChunk chunk,
                            Map<String, Set<String>> gramCache) {
        Integer exactFrequency = chunk.frequencies().get(queryTerm);
        if (exactFrequency != null) {
            return new TermMatch(exactFrequency, 1);
        }
        int queryLength = queryTerm.codePointCount(0, queryTerm.length());
        if (queryLength < MIN_FUZZY_LENGTH) {
            return TermMatch.absent();
        }
        String prefix = queryTerm.substring(0, queryTerm.offsetByCodePoints(0, FUZZY_PREFIX_LENGTH));
        TermMatch best = TermMatch.absent();
        double bestSimilarity = 0;
        for (Map.Entry<String, Integer> candidate : chunk.frequencies().entrySet()) {
            String term = candidate.getKey();
            int length = term.codePointCount(0, term.length());
            if (length < MIN_FUZZY_LENGTH
                    || Math.abs(length - queryLength) > MAX_FUZZY_LENGTH_DELTA
                    || !term.startsWith(prefix)) {
                continue;
            }
            double similarity = dice(queryTerm, term, gramCache);
            if (similarity >= MIN_DICE_SIMILARITY && similarity > bestSimilarity) {
                best = new TermMatch(candidate.getValue(), similarity * FUZZY_WEIGHT);
                bestSimilarity = similarity;
            }
        }
        return best;
    }

    private double dice(String first, String second, Map<String, Set<String>> cache) {
        Set<String> firstGrams = cache.computeIfAbsent(first, this::trigrams);
        Set<String> secondGrams = cache.computeIfAbsent(second, this::trigrams);
        long overlap = firstGrams.stream().filter(secondGrams::contains).count();
        return 2.0 * overlap / (firstGrams.size() + secondGrams.size());
    }

    private Set<String> trigrams(String text) {
        int[] codePoints = text.codePoints().toArray();
        Set<String> grams = new HashSet<>();
        for (int index = 0; index <= codePoints.length - 3; index++) {
            grams.add(new String(codePoints, index, 3));
        }
        return Set.copyOf(grams);
    }

    private List<String> terms(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е');
        normalized = BROKEN_WORD.matcher(normalized).replaceAll("$1$2");
        Matcher matcher = WORD.matcher(normalized);
        List<String> result = new ArrayList<>();
        while (matcher.find()) {
            String term = matcher.group();
            if (term.codePointCount(0, term.length()) > 1
                    || term.codePoints().allMatch(Character::isDigit)
                    || term.codePoints().anyMatch(codePoint ->
                    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)) {
                result.add(term);
            }
        }
        return result;
    }

    public record RankedChunk(DocumentChunk chunk, double score) {
        public RankedChunk {
            Objects.requireNonNull(chunk, "Ranked chunk must not be null");
            if (!Double.isFinite(score) || score <= 0) {
                throw new IllegalArgumentException("Relevance score must be positive and finite");
            }
        }
    }

    private record IndexedChunk(DocumentChunk chunk, Map<String, Integer> frequencies, int length) {
    }

    private record TermMatch(int frequency, double quality) {
        private static TermMatch absent() {
            return new TermMatch(0, 0);
        }

        private boolean present() {
            return frequency > 0;
        }
    }
}
