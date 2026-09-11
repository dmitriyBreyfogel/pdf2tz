package com.pdf2tz.backend.application.pdf.cleaning;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Формирует профиль служебного шума по сырому тексту PDF-документа.
 *
 * <p>Класс не очищает документ самостоятельно. Его задача - проанализировать
 * уже извлечённый постраничный текст и определить, какие строки и фрагменты
 * похожи на водяные знаки, колонтитулы и другой повторяющийся технический
 * шум конкретного документа.</p>
 */
@Component
public class DocumentNoiseProfileBuilder {

    /**
     * Минимальная доля страниц, на которых строка должна встретиться,
     * чтобы стать кандидатом на удаление целиком.
     */
    private static final double LINE_NOISE_PAGE_RATIO = 0.35;

    /**
     * Минимальная доля страниц для коротких строк, похожих на фрагменты
     * распавшегося водяного знака.
     *
     * <p>Такие строки опасно удалять по одной: короткий фрагмент может быть
     * полезным значением таблицы. Поэтому порог намеренно высокий.</p>
     */
    private static final double FRAGMENTED_LINE_NOISE_PAGE_RATIO = 0.75;

    /**
     * Минимальная доля страниц, на которых фрагмент внутри строки должен
     * встретиться, чтобы стать кандидатом на inline-удаление.
     */
    private static final double INLINE_NOISE_PAGE_RATIO = 0.50;

    /**
     * Минимальное количество страниц для строкового шума.
     * Защищает маленькие документы от слишком агрессивной очистки.
     */
    private static final int MIN_LINE_OCCURRENCES = 3;

    /**
     * Минимальная длина строки, которую имеет смысл анализировать как шум.
     */
    private static final int MIN_LINE_LENGTH = 8;

    /**
     * Минимальная длина короткой строки, которую можно рассматривать как
     * фрагмент распавшегося водяного знака.
     */
    private static final int MIN_FRAGMENTED_LINE_LENGTH = 2;

    /**
     * Максимальная длина короткой строки, которую можно рассматривать как
     * фрагмент распавшегося водяного знака.
     */
    private static final int MAX_FRAGMENTED_LINE_LENGTH = 7;

    /**
     * Минимальное количество разных коротких повторяющихся строк, нужное
     * для включения режима очистки распавшегося водяного знака.
     *
     * <p>Одна-две короткие повторяющиеся строки ещё не доказывают наличие
     * watermark. Кластер из нескольких таких строк уже является сильным
     * признаком, что PDF-слой разбил один водяной знак на мелкие части.</p>
     */
    private static final int MIN_FRAGMENTED_LINE_NOISE_GROUP_SIZE = 6;

    /**
     * Количество строк сверху и снизу страницы, которые считаются зоной
     * вероятных колонтитулов.
     */
    private static final int PAGE_EDGE_LINE_COUNT = 3;

    /**
     * Минимальное количество страниц для inline-шума.
     * Inline-удаление опаснее удаления целой строки, поэтому оно не должно
     * срабатывать по единичным совпадениям.
     */
    private static final int MIN_INLINE_OCCURRENCES = 3;

    /**
     * Минимальная длина доменного фрагмента для inline-удаления.
     *
     * <p>Короткие обрывки доменов вроде {@code r.go} или {@code v.ru} часто появляются из распавшегося
     * watermark и особенно заметны внутри табличных ячеек. Они попадают в inline-шум только при высокой
     * повторяемости по страницам документа, поэтому единичные полезные значения не должны удаляться.</p>
     */
    private static final int MIN_DOMAIN_FRAGMENT_LENGTH = 4;

    /**
     * Короткие самостоятельные значения, которые нельзя считать шумом только
     * из-за повторяемости на страницах.
     *
     * <p>Список не описывает конкретный watermark. Он защищает типовые
     * табличные значения и единицы измерения от удаления при обработке
     * медицинских инструкций.</p>
     */
    private static final Set<String> PROTECTED_SHORT_LINES = Set.of(
            "да",
            "нет",
            "yes",
            "no",
            "on",
            "off",
            "мл",
            "мг",
            "мкг",
            "кг",
            "мм",
            "см",
            "мин",
            "сек",
            "ml",
            "mg",
            "mcg",
            "kg",
            "mm",
            "cm",
            "min",
            "sec"
    );

    /**
     * Шаблон для поиска URL и доменных имён внутри строк.
     * Такие фрагменты часто относятся к водяным знакам и служебным подписям.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://\\S+|www\\.\\S+|\\b[\\p{L}\\p{N}_-]+(?:\\.[\\p{L}\\p{N}_-]+)*\\.[\\p{L}]{2,}(?:/\\S*)?|(?<!\\S)\\.[\\p{L}]{2,}\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS
    );

    /**
     * Строит профиль служебного шума для конкретного PDF-документа.
     *
     * <p>Профиль строится динамически: класс не знает заранее текст водяного
     * знака, а выводит шум по повторяемости строк и фрагментов на разных
     * страницах документа.</p>
     *
     * @param document сырой постраничный текст PDF-документа
     * @return профиль найденного служебного шума
     */
    public DocumentNoiseProfile build(ExtractedDocument document) {
        Set<String> lineNoise = findLineNoise(document);
        Set<String> inlineNoise = findInlineNoise(document);

        return new DocumentNoiseProfile(
                lineNoise,
                inlineNoise
        );
    }

    /**
     * Находит строки, которые можно удалить из документа целиком.
     *
     * <p>Алгоритм считает не общее количество повторов строки, а количество
     * страниц, на которых она встретилась. Это защищает от ситуации, когда
     * полезная строка несколько раз повторилась на одной странице и была
     * ошибочно принята за служебный шум.</p>
     *
     * <p>Повторяющаяся строка считается шумом только при дополнительных
     * признаках: она часто находится в верхней/нижней части страницы или
     * содержит URL/домен. Короткие фрагменты распавшегося watermark
     * анализируются отдельно.</p>
     */
    private Set<String> findLineNoise(ExtractedDocument document) {
        Map<String, Set<Integer>> linePages = new HashMap<>();
        Map<String, Set<Integer>> edgeLinePages = new HashMap<>();
        Map<String, Set<Integer>> fragmentedLinePages = new HashMap<>();

        for (ExtractedPage page : document.pages()) {
            List<String> lines = normalizedLines(page.text());

            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);

                if (isFragmentedLineNoiseCandidate(line)) {
                    registerOccurrence(fragmentedLinePages, line, page.pageNumber());
                }

                if (line.length() < MIN_LINE_LENGTH) {
                    continue;
                }

                registerOccurrence(linePages, line, page.pageNumber());

                if (isPageEdgeLine(index, lines.size())) {
                    registerOccurrence(edgeLinePages, line, page.pageNumber());
                }
            }
        }

        int minPageCount = minPageCount(
                document.pages().size(),
                LINE_NOISE_PAGE_RATIO,
                MIN_LINE_OCCURRENCES
        );
        Set<String> result = new HashSet<>();

        linePages.forEach((line, pageNumbers) -> {
            int pageOccurrenceCount = pageNumbers.size();
            int edgeOccurrenceCount = edgeLinePages
                    .getOrDefault(line, Set.of())
                    .size();

            boolean repeatedOnPageEdge = edgeOccurrenceCount >= minPageCount;
            boolean containsUrl = containsUrlLikeFragment(line);

            if (pageOccurrenceCount >= minPageCount
                    && (repeatedOnPageEdge || containsUrl)
                    && isSafeLineNoise(line)) {
                result.add(line);
            }
        });

        result.addAll(findFragmentedLineNoise(
                fragmentedLinePages,
                document.pages().size()
        ));

        return result;
    }

    /**
     * Находит короткие строки, на которые PDF/OCR может разбить один водяной знак.
     *
     * <p>Метод работает только при наличии группы таких строк. Это снижает риск
     * удалить полезное короткое значение, которое случайно повторилось на многих
     * страницах. Найденные фрагменты попадают именно в {@code lineNoise}, а не в
     * {@code inlineNoise}: их можно удалять только как самостоятельные строки.</p>
     */
    private Set<String> findFragmentedLineNoise(
            Map<String, Set<Integer>> fragmentedLinePages,
            int pageCount
    ) {
        int minPageCount = minPageCount(
                pageCount,
                FRAGMENTED_LINE_NOISE_PAGE_RATIO,
                MIN_LINE_OCCURRENCES
        );

        Set<String> candidates = new HashSet<>();

        fragmentedLinePages.forEach((line, pageNumbers) -> {
            if (pageNumbers.size() >= minPageCount) {
                candidates.add(line);
            }
        });

        if (candidates.size() < MIN_FRAGMENTED_LINE_NOISE_GROUP_SIZE) {
            return Set.of();
        }

        return candidates;
    }

    /**
     * Находит фрагменты, которые нужно удалять внутри полезных строк.
     *
     * <p>Такой шум опаснее обычного строкового шума, потому что очистка будет
     * вырезать часть строки или ячейки таблицы. Поэтому inline-режим сейчас
     * ограничен только надёжными URL/доменными фрагментами и строгим порогом
     * повторяемости.</p>
     */
    private Set<String> findInlineNoise(ExtractedDocument document) {
        Map<String, Set<Integer>> fragmentPages = new HashMap<>();

        for (ExtractedPage page : document.pages()) {
            normalizedLines(page.text()).forEach(line ->
                    collectUrlFragments(line, page.pageNumber(), fragmentPages)
            );
        }

        int minPageCount = minPageCount(
                document.pages().size(),
                INLINE_NOISE_PAGE_RATIO,
                MIN_INLINE_OCCURRENCES
        );

        Set<String> result = new HashSet<>();

        fragmentPages.forEach((fragment, pageNumbers) -> {
            if (pageNumbers.size() >= minPageCount && isSafeInlineNoise(fragment)) {
                result.add(fragment);
            }
        });

        return result;
    }

    /**
     * Собирает URL и доменные имена как кандидаты на inline-шум.
     *
     * <p>URL часто попадают в текст из водяных знаков и служебных подписей.
     * Они собираются отдельно от обычных словесных фрагментов, потому что
     * домен может быть коротким, но всё равно иметь служебную природу.</p>
     */
    private void collectUrlFragments(
            String line,
            int pageNumber,
            Map<String, Set<Integer>> fragmentPages
    ) {
        Matcher matcher = URL_PATTERN.matcher(line);

        while (matcher.find()) {
            String fragment = normalizeForCompare(matcher.group());

            if (isReliableUrlFragment(fragment)) {
                registerOccurrence(fragmentPages, fragment, pageNumber);
            }
        }
    }

    private List<String> normalizedLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return text.lines()
                .map(this::normalizeForCompare)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private void registerOccurrence(
            Map<String, Set<Integer>> occurrences,
            String value,
            int pageNumber
    ) {
        occurrences
                .computeIfAbsent(value, ignored -> new HashSet<>())
                .add(pageNumber);
    }

    /**
     * Рассчитывает минимальное количество страниц, нужное для признания
     * строки или фрагмента повторяющимся шумом.
     *
     * <p>Порог состоит из относительной части и абсолютного минимума:
     * относительная часть масштабируется под размер документа, а абсолютный
     * минимум не даёт агрессивно чистить короткие документы.</p>
     */
    private int minPageCount(
            int pageCount,
            double ratio,
            int minOccurrences
    ) {
        return Math.max(
                minOccurrences,
                (int) Math.ceil(pageCount * ratio)
        );
    }

    private boolean isPageEdgeLine(int lineIndex, int lineCount) {
        return lineIndex < PAGE_EDGE_LINE_COUNT
                || lineIndex >= lineCount - PAGE_EDGE_LINE_COUNT;
    }

    private boolean isSafeLineNoise(String line) {
        return hasLetters(line) && !isSectionHeading(line);
    }

    private boolean isSafeInlineNoise(String fragment) {
        return hasLetters(fragment) && !isSectionHeading(fragment);
    }

    private boolean isFragmentedLineNoiseCandidate(String line) {
        int length = line.length();

        return length >= MIN_FRAGMENTED_LINE_LENGTH
                && length <= MAX_FRAGMENTED_LINE_LENGTH
                && hasLetters(line)
                && !containsDigit(line)
                && !isSectionHeading(line)
                && !isProtectedShortLine(line);
    }

    private boolean isReliableUrlFragment(String fragment) {
        return startsWithUrlPrefix(fragment)
                || isStandaloneTopLevelDomain(fragment)
                || isShortDomainFragment(fragment);
    }

    private boolean containsUrlLikeFragment(String text) {
        return URL_PATTERN.matcher(text).find();
    }

    private boolean isSectionHeading(String text) {
        return text.matches("\\d+(\\.\\d+)*\\.?\\s+.+");
    }

    private boolean hasLetters(String text) {
        return text.codePoints().anyMatch(Character::isLetter);
    }

    private boolean containsDigit(String text) {
        return text.codePoints().anyMatch(Character::isDigit);
    }

    private boolean isProtectedShortLine(String line) {
        return PROTECTED_SHORT_LINES.contains(normalizeWord(line));
    }

    private boolean startsWithUrlPrefix(String text) {
        return text.startsWith("http://")
                || text.startsWith("https://")
                || text.startsWith("www.");
    }

    private boolean isStandaloneTopLevelDomain(String text) {
        return text.matches("\\.[\\p{L}]{2,}");
    }

    private boolean isShortDomainFragment(String text) {
        return text.length() >= MIN_DOMAIN_FRAGMENT_LENGTH
                && text.contains(".")
                && containsAsciiLetter(text);
    }

    private boolean containsAsciiLetter(String text) {
        return text.chars().anyMatch(character ->
                (character >= 'a' && character <= 'z')
                        || (character >= 'A' && character <= 'Z')
        );
    }

    private String normalizeWord(String word) {
        return word.replaceAll("^\\p{P}+|\\p{P}+$", "");
    }

    private String normalizeForCompare(String text) {
        return text
                .replace('\u00A0', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
