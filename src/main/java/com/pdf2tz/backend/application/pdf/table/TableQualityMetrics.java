package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Метрики качества табличного фрагмента, по которым можно отличить реальную таблицу
 * от ошибочно извлечённого текста, подписей схем или набора разрозненных символов.
 *
 * <p>Record не решает, нужно ли оставлять фрагмент в итоговом документе. Он только
 * собирает признаки фрагмента и рассчитывает суммарную оценку. Само решение остаётся
 * в {@link TableQualityFilter}, чтобы сбор метрик и политика фильтрации не смешивались
 * в одном классе.</p>
 *
 * @param rowCount количество строк во фрагменте
 * @param columnCount количество колонок во фрагменте
 * @param filledCellCount количество непустых ячеек
 * @param tokenCount количество текстовых токенов
 * @param singleCharacterTokenCount количество односимвольных токенов
 * @param meaningfulWordCount количество содержательных слов
 * @param multiColumnRowCount количество строк с двумя и более заполненными ячейками
 * @param keyValueRowCount количество строк, похожих на пару "ключ-значение"
 * @param abbreviationKeyValueRowCount количество строк, где ключ похож на аббревиатуру
 * @param numericValueRowCount количество строк, где значение содержит число
 * @param longSentenceRowCount количество строк, похожих на обычный текстовый абзац
 * @param tableOfContentsRowCount количество строк, похожих на пункт оглавления с номером страницы
 * @param hasHeaderTerms есть ли во фрагменте слова, характерные для заголовков таблиц
 * @param hasTableOfContentsTitle есть ли во фрагменте явный заголовок оглавления
 * @param score суммарная эвристическая оценка качества фрагмента
 */
public record TableQualityMetrics(
        int rowCount,
        int columnCount,
        int filledCellCount,
        int tokenCount,
        int singleCharacterTokenCount,
        int meaningfulWordCount,
        int multiColumnRowCount,
        int keyValueRowCount,
        int abbreviationKeyValueRowCount,
        int numericValueRowCount,
        int longSentenceRowCount,
        int tableOfContentsRowCount,
        boolean hasHeaderTerms,
        boolean hasTableOfContentsTitle,
        int score
) {

    /**
     * Минимальная длина слова, которое считается содержательным текстом таблицы.
     */
    private static final int MIN_MEANINGFUL_WORD_LENGTH = 3;

    /**
     * Максимальная доля коротких односимвольных токенов, после которой фрагмент
     * получает штраф как потенциальный набор подписей на схеме.
     */
    private static final double MAX_SINGLE_CHARACTER_TOKEN_RATIO = 0.50;

    /**
     * Максимальная доля строк, похожих на обычные предложения, если у фрагмента
     * нет сильных табличных признаков.
     */
    private static final double MAX_LONG_SENTENCE_ROW_RATIO = 0.60;

    /**
     * Минимальная доля строк с двумя и более заполненными ячейками для структурной
     * многострочной таблицы.
     */
    private static final double MIN_MULTI_COLUMN_ROW_RATIO = 0.45;

    /**
     * Минимальная доля строк, похожих на оглавление, после которой фрагмент безопаснее
     * оставить обычным текстом, а не структурированной таблицей.
     */
    private static final double MIN_TABLE_OF_CONTENTS_ROW_RATIO = 0.45;

    /**
     * Табличные слова, которые часто встречаются в заголовках медицинских инструкций.
     *
     * <p>Это слабый положительный сигнал, а не обязательный словарь предметной области.
     * Фрагмент не обязан содержать эти слова, чтобы считаться таблицей, и наличие такого
     * слова не делает любой текст таблицей автоматически.</p>
     */
    private static final Set<String> TABLE_HEADER_TERMS = Set.of(
            "таблица",
            "наименование",
            "значение",
            "количество",
            "параметр",
            "характеристика",
            "обозначение",
            "расшифровка",
            "описание",
            "код",
            "артикул",
            "единица",
            "table",
            "name",
            "value",
            "quantity",
            "parameter",
            "characteristic",
            "description",
            "code"
    );

    /**
     * Заголовки оглавлений, которые часто извлекаются Tabula как одноколоночные таблицы.
     *
     * <p>Это отрицательный структурный признак: оглавление важно сохранить в тексте,
     * но оно не является таблицей данных для будущего технического задания.</p>
     */
    private static final Set<String> TABLE_OF_CONTENTS_TITLES = Set.of(
            "table of contents",
            "contents",
            "содержание",
            "оглавление"
    );

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[\\s|]+");

    /**
     * Точечный заполнитель оглавления со ссылкой на страницу, в том числе вида 6-18.
     * Проверяется в каждой ячейке: Tabula может разбить один пункт на несколько колонок.
     */
    private static final Pattern CONTENTS_LEADER_PATTERN = Pattern.compile(
            "\\.{3,}\\s*\\d{1,4}(?:[-–]\\d{1,4})?"
    );

    /**
     * Собирает метрики качества по нормализованному фрагменту таблицы.
     *
     * @param fragment нормализованный табличный фрагмент
     * @return рассчитанные метрики качества
     */
    public static TableQualityMetrics from(TableFragment fragment) {
        MetricAccumulator accumulator = new MetricAccumulator(fragment);

        return accumulator.toMetrics();
    }

    /**
     * Возвращает долю односимвольных токенов во всём тексте фрагмента.
     *
     * @return значение от {@code 0} до {@code 1}; {@code 0}, если во фрагменте нет токенов
     */
    public double singleCharacterTokenRatio() {
        if (tokenCount == 0) {
            return 0;
        }

        return (double) singleCharacterTokenCount / tokenCount;
    }

    /**
     * Возвращает долю строк, в которых заполнены минимум две ячейки.
     *
     * @return значение от {@code 0} до {@code 1}
     */
    public double multiColumnRowRatio() {
        return (double) multiColumnRowCount / rowCount;
    }

    /**
     * Возвращает долю строк, похожих на обычные текстовые предложения.
     *
     * @return значение от {@code 0} до {@code 1}
     */
    public double longSentenceRowRatio() {
        return (double) longSentenceRowCount / rowCount;
    }

    /**
     * Возвращает долю заполненных ячеек относительно всей прямоугольной сетки фрагмента.
     *
     * @return значение от {@code 0} до {@code 1}
     */
    public double filledCellRatio() {
        return (double) filledCellCount / (rowCount * columnCount);
    }

    /**
     * Возвращает долю строк, похожих на пункты оглавления.
     *
     * @return значение от {@code 0} до {@code 1}
     */
    public double tableOfContentsRowRatio() {
        return (double) tableOfContentsRowCount / rowCount;
    }

    /**
     * Проверяет, похож ли фрагмент на оглавление, ошибочно принятое за таблицу.
     *
     * @return {@code true}, если фрагмент имеет явный заголовок оглавления или
     * достаточную долю строк с названием раздела и номером страницы
     */
    public boolean isTableOfContentsLike() {
        return hasTableOfContentsTitle
                || tableOfContentsRowRatio() >= MIN_TABLE_OF_CONTENTS_ROW_RATIO;
    }

    /**
     * Собирает признаки фрагмента в одном проходе по строкам и ячейкам.
     *
     * <p>Класс остаётся приватной деталью {@link TableQualityMetrics}: наружу отдаётся
     * только готовый immutable-result в виде record.</p>
     */
    private static final class MetricAccumulator {

        private final TableFragment fragment;

        private int filledCellCount;
        private int tokenCount;
        private int singleCharacterTokenCount;
        private int meaningfulWordCount;
        private int multiColumnRowCount;
        private int keyValueRowCount;
        private int abbreviationKeyValueRowCount;
        private int numericValueRowCount;
        private int longSentenceRowCount;
        private int tableOfContentsRowCount;
        private boolean hasHeaderTerms;
        private boolean hasTableOfContentsTitle;

        private MetricAccumulator(TableFragment fragment) {
            this.fragment = fragment;
        }

        private TableQualityMetrics toMetrics() {
            collect();

            return new TableQualityMetrics(
                    fragment.rowCount(),
                    fragment.columnCount(),
                    filledCellCount,
                    tokenCount,
                    singleCharacterTokenCount,
                    meaningfulWordCount,
                    multiColumnRowCount,
                    keyValueRowCount,
                    abbreviationKeyValueRowCount,
                    numericValueRowCount,
                    longSentenceRowCount,
                    tableOfContentsRowCount,
                    hasHeaderTerms,
                    hasTableOfContentsTitle,
                    score()
            );
        }

        private void collect() {
            for (TableRow row : fragment.rows()) {
                collectRow(row);
            }
        }

        private void collectRow(TableRow row) {
            List<String> filledCells = row.cells().stream()
                    .map(TableCell::text)
                    .filter(text -> !text.isBlank())
                    .toList();

            filledCellCount += filledCells.size();

            if (filledCells.size() >= 2) {
                multiColumnRowCount++;
            }

            String rowText = String.join(" ", filledCells);

            if (containsHeaderTerms(rowText)) {
                hasHeaderTerms = true;
            }

            if (isTableOfContentsTitle(rowText)) {
                hasTableOfContentsTitle = true;
            }

            if (isTableOfContentsRow(filledCells)) {
                tableOfContentsRowCount++;
            }

            if (isLongSentenceRow(filledCells)) {
                longSentenceRowCount++;
            }

            if (isKeyValueRow(filledCells)) {
                keyValueRowCount++;
            }

            if (isAbbreviationKeyValueRow(filledCells)) {
                abbreviationKeyValueRowCount++;
            }

            if (isNumericValueRow(filledCells)) {
                numericValueRowCount++;
            }

            collectTokens(filledCells);
        }

        private void collectTokens(List<String> filledCells) {
            filledCells.stream()
                    .flatMap(text -> TOKEN_SPLIT_PATTERN.splitAsStream(text))
                    .map(this::normalizeToken)
                    .filter(token -> !token.isBlank())
                    .forEach(token -> {
                        tokenCount++;

                        if (isSingleCharacterToken(token)) {
                            singleCharacterTokenCount++;
                        }

                        if (isMeaningfulWord(token)) {
                            meaningfulWordCount++;
                        }
                    });
        }

        private int score() {
            int result = 0;

            if (hasHeaderTerms) {
                result += 3;
            }

            if (fragment.rowCount() >= 2) {
                result += 1;
            }

            if (fragment.rowCount() >= 3) {
                result += 1;
            }

            if (fragment.columnCount() >= 2) {
                result += 1;
            }

            if (multiColumnRowRatio() >= MIN_MULTI_COLUMN_ROW_RATIO) {
                result += 2;
            }

            if (keyValueRowCount >= 2) {
                result += 2;
            }
            else if (keyValueRowCount == 1) {
                result += 1;
            }

            if (numericValueRowCount > 0) {
                result += 1;
            }

            if (meaningfulWordCount >= 4) {
                result += 1;
            }

            if (singleCharacterTokenRatio() > MAX_SINGLE_CHARACTER_TOKEN_RATIO) {
                result -= 3;
            }

            if (longSentenceRowRatio() > MAX_LONG_SENTENCE_ROW_RATIO
                    && keyValueRowCount < 2
                    && !hasHeaderTerms) {
                result -= 2;
            }

            if (tableOfContentsRowRatio() >= MIN_TABLE_OF_CONTENTS_ROW_RATIO
                    || hasTableOfContentsTitle) {
                result -= 4;
            }

            if (multiColumnRowRatio() < MIN_MULTI_COLUMN_ROW_RATIO
                    && !hasHeaderTerms) {
                result -= 2;
            }

            return result;
        }

        private double singleCharacterTokenRatio() {
            if (tokenCount == 0) {
                return 0;
            }

            return (double) singleCharacterTokenCount / tokenCount;
        }

        private double multiColumnRowRatio() {
            return (double) multiColumnRowCount / fragment.rowCount();
        }

        private double longSentenceRowRatio() {
            return (double) longSentenceRowCount / fragment.rowCount();
        }

        private double tableOfContentsRowRatio() {
            return (double) tableOfContentsRowCount / fragment.rowCount();
        }

        private boolean isLongSentenceRow(List<String> filledCells) {
            return filledCells.size() == 1
                    && words(filledCells.get(0)).size() >= 7;
        }

        private boolean isKeyValueRow(List<String> filledCells) {
            if (filledCells.size() < 2) {
                return false;
            }

            String key = filledCells.get(0);
            String value = String.join(" ", filledCells.subList(1, filledCells.size()));

            return words(key).size() <= 4
                    && !value.isBlank()
                    && (isNumericValue(value)
                    || isAbbreviationLike(key)
                    || words(value).size() >= 2);
        }

        private boolean isAbbreviationKeyValueRow(List<String> filledCells) {
            if (filledCells.size() < 2) {
                return false;
            }

            return isAbbreviationLike(filledCells.get(0))
                    && words(String.join(" ", filledCells.subList(1, filledCells.size()))).size() >= 2;
        }

        private boolean isNumericValueRow(List<String> filledCells) {
            return filledCells.size() >= 2
                    && isNumericValue(String.join(" ", filledCells.subList(1, filledCells.size())));
        }

        private boolean isNumericValue(String text) {
            return text.codePoints().anyMatch(Character::isDigit);
        }

        /**
         * Распознаёт типичную строку оглавления без привязки к конкретному языку:
         * несколько слов названия и короткая ссылка на страницу в конце строки.
         */
        private boolean isTableOfContentsRow(List<String> filledCells) {
            if (filledCells.stream().anyMatch(text -> CONTENTS_LEADER_PATTERN.matcher(text).find())) {
                return true;
            }
            if (filledCells.size() != 1) {
                return false;
            }

            String text = filledCells.get(0).trim();
            List<String> words = words(text);

            return words.size() >= 2
                    && endsWithPageReference(text);
        }

        private boolean endsWithPageReference(String text) {
            String[] tokens = TOKEN_SPLIT_PATTERN.split(text.trim());

            if (tokens.length == 0) {
                return false;
            }

            String lastToken = normalizeToken(tokens[tokens.length - 1])
                    .toLowerCase(Locale.ROOT);

            return lastToken.matches("\\d{1,4}")
                    || lastToken.matches("[ivxlcdm]{1,8}");
        }

        private boolean isAbbreviationLike(String text) {
            String compactLetters = text.codePoints()
                    .filter(Character::isLetter)
                    .collect(
                            StringBuilder::new,
                            StringBuilder::appendCodePoint,
                            (builder, other) -> builder.append(other)
                    )
                    .toString();

            if (compactLetters.length() < 2 || compactLetters.length() > 8) {
                return false;
            }

            long uppercaseCount = compactLetters.codePoints()
                    .filter(Character::isUpperCase)
                    .count();

            return uppercaseCount >= Math.max(2, compactLetters.length() - 1);
        }

        private boolean containsHeaderTerms(String text) {
            String normalizedText = text.toLowerCase(Locale.ROOT);

            return TABLE_HEADER_TERMS.stream().anyMatch(normalizedText::contains);
        }

        private boolean isTableOfContentsTitle(String text) {
            String normalizedText = text.toLowerCase(Locale.ROOT);

            return TABLE_OF_CONTENTS_TITLES.stream().anyMatch(normalizedText::contains);
        }

        private List<String> words(String text) {
            return TOKEN_SPLIT_PATTERN.splitAsStream(text)
                    .map(this::normalizeToken)
                    .filter(token -> !token.isBlank())
                    .filter(token -> token.codePoints().anyMatch(Character::isLetter))
                    .toList();
        }

        private boolean isMeaningfulWord(String token) {
            return token.length() >= MIN_MEANINGFUL_WORD_LENGTH
                    && token.codePoints().anyMatch(Character::isLetter);
        }

        private boolean isSingleCharacterToken(String token) {
            return token.codePointCount(0, token.length()) == 1;
        }

        private String normalizeToken(String token) {
            return token.replaceAll("^\\p{P}+|\\p{P}+$", "")
                    .trim();
        }
    }
}
