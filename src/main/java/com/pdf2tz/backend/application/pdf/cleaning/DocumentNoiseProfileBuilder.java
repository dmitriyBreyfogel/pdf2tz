package com.pdf2tz.backend.application.pdf.cleaning;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
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
     * Более строгая доля страниц для строки, которая повторяется не только
     * в начале или конце страницы.
     */
    private static final double STRONG_LINE_NOISE_PAGE_RATIO = 0.75;

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
     * Минимальная длина inline-фрагмента.
     * Короткие слова и устойчивые медицинские термины не должны удаляться
     * только из-за частого появления.
     */
    private static final int MIN_INLINE_LENGTH = 24;

    /**
     * Размер фрагмента в словах для поиска повторяющихся inline-кандидатов.
     */
    private static final int INLINE_FRAGMENT_WORD_COUNT = 4;

    /**
     * Шаблон для поиска URL и доменных имён внутри строк.
     * Такие фрагменты часто относятся к водяным знакам и служебным подписям.
     */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://\\S+|www\\.\\S+|\\b[\\p{L}\\p{N}_-]+(?:\\.[\\p{L}\\p{N}_-]+)*\\.[\\p{L}]{2,}(?:/\\S*)?)",
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
     * признаках: она часто находится в верхней/нижней части страницы,
     * встречается почти по всему документу или содержит URL/домен.</p>
     */
    private Set<String> findLineNoise(ExtractedDocument document) {
        Map<String, Set<Integer>> linePages = new HashMap<>();
        Map<String, Set<Integer>> edgeLinePages = new HashMap<>();

        for (ExtractedPage page : document.pages()) {
            List<String> lines = normalizedLines(page.text());

            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);

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
        int strongMinPageCount = minPageCount(
                document.pages().size(),
                STRONG_LINE_NOISE_PAGE_RATIO,
                MIN_LINE_OCCURRENCES
        );

        Set<String> result = new HashSet<>();

        linePages.forEach((line, pageNumbers) -> {
            int pageOccurrenceCount = pageNumbers.size();
            int edgeOccurrenceCount = edgeLinePages
                    .getOrDefault(line, Set.of())
                    .size();

            boolean repeatedOnPageEdge = edgeOccurrenceCount >= minPageCount;
            boolean repeatedAlmostEverywhere = pageOccurrenceCount >= strongMinPageCount;
            boolean containsUrl = containsUrlLikeFragment(line);

            if (pageOccurrenceCount >= minPageCount
                    && (repeatedOnPageEdge || repeatedAlmostEverywhere || containsUrl)
                    && isSafeLineNoise(line)) {
                result.add(line);
            }
        });

        return result;
    }

    /**
     * Находит фрагменты, которые нужно удалять внутри полезных строк.
     *
     * <p>Такой шум опаснее обычного строкового шума, потому что очистка будет
     * вырезать часть строки или ячейки таблицы. Поэтому для inline-фрагментов
     * используется более строгий порог повторяемости.</p>
     */
    private Set<String> findInlineNoise(ExtractedDocument document) {
        Map<String, Set<Integer>> fragmentPages = new HashMap<>();

        for (ExtractedPage page : document.pages()) {
            normalizedLines(page.text()).forEach(line -> {
                collectUrlFragments(line, page.pageNumber(), fragmentPages);
                collectWordFragments(line, page.pageNumber(), fragmentPages);
            });
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
            registerOccurrence(fragmentPages, fragment, pageNumber);
        }
    }

    /**
     * Собирает повторяющиеся фрагменты из последовательностей слов.
     *
     * <p>Метод использует скользящее окно: из строки берётся несколько слов
     * подряд, затем окно сдвигается на одно слово. Так можно найти фрагмент
     * водяного знака даже тогда, когда он вклинился внутрь обычного текста.</p>
     */
    private void collectWordFragments(
            String line,
            int pageNumber,
            Map<String, Set<Integer>> fragmentPages
    ) {
        List<String> words = Arrays.stream(line.split("\\s+"))
                .map(this::normalizeWord)
                .filter(word -> !word.isBlank())
                .toList();

        if (words.size() < INLINE_FRAGMENT_WORD_COUNT) {
            return;
        }

        for (int index = 0; index <= words.size() - INLINE_FRAGMENT_WORD_COUNT; index++) {
            String fragment = String.join(
                    " ",
                    words.subList(index, index + INLINE_FRAGMENT_WORD_COUNT)
            );

            if (fragment.length() >= MIN_INLINE_LENGTH) {
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

    private boolean containsUrlLikeFragment(String text) {
        return URL_PATTERN.matcher(text).find();
    }

    private boolean isSectionHeading(String text) {
        return text.matches("\\d+(\\.\\d+)*\\.?\\s+.+");
    }

    private boolean hasLetters(String text) {
        return text.codePoints().anyMatch(Character::isLetter);
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
