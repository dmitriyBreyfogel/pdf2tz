package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Отбрасывает нормализованные табличные фрагменты, которые больше похожи на ошибку извлечения, чем на таблицу.
 *
 * <p>Tabula может принять за таблицу подписи на схемах, наборы одиночных букв, номера деталей или обычный
 * текстовый блок. Этот фильтр работает после {@link TableNormalizer}, когда ячейки уже очищены от watermark,
 * пустые строки удалены, а технические пустые колонки убраны. Поэтому здесь можно оценивать именно качество
 * итогового табличного представления, не смешивая эту ответственность с извлечением или нормализацией.</p>
 */
@Component
public class TableQualityFilter {

    /**
     * Минимальная оценка качества, при которой фрагмент считается достаточно похожим на таблицу.
     *
     * <p>Порог намеренно консервативный: сомнительный кандидат безопаснее оставить в общем тексте документа,
     * чем добавить в итоговые таблицы как ложную структуру.</p>
     */
    private static final int MIN_ACCEPTABLE_SCORE = 3;

    /**
     * Минимальная длина слова, которое считается содержательным текстом таблицы.
     */
    private static final int MIN_MEANINGFUL_WORD_LENGTH = 3;

    /**
     * Максимальная доля коротких односимвольных токенов, допустимая для небольшой таблицы.
     */
    private static final double MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO = 0.50;

    /**
     * Максимальная доля строк, похожих на обычные предложения, если у фрагмента нет сильных табличных признаков.
     */
    private static final double MAX_LONG_SENTENCE_ROW_RATIO = 0.60;

    /**
     * Минимальная доля строк с двумя и более заполненными ячейками для структурной многострочной таблицы.
     */
    private static final double MIN_MULTI_COLUMN_ROW_RATIO = 0.45;

    /**
     * Табличные слова, которые часто встречаются в заголовках медицинских инструкций.
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

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[\\s|]+");

    /**
     * Возвращает только те фрагменты, которые достаточно похожи на реальные таблицы.
     *
     * <p>Метод не исправляет содержимое таблиц и не меняет их порядок. Он только удаляет слабые кандидаты,
     * чтобы мусор из схем и обычный текст, ошибочно распознанный как таблица, не попадали в итоговую модель.</p>
     *
     * @param fragments нормализованные фрагменты таблиц
     * @return фрагменты, прошедшие проверку качества
     */
    public List<TableFragment> filter(List<TableFragment> fragments) {
        Objects.requireNonNull(fragments, "Table fragments must not be null");

        return fragments.stream()
                .map(fragment -> Objects.requireNonNull(fragment, "Table fragment must not be null"))
                .filter(this::isHighQuality)
                .toList();
    }

    private boolean isHighQuality(TableFragment fragment) {
        QualityMetrics metrics = QualityMetrics.from(fragment);

        if (metrics.meaningfulWordCount() == 0) {
            return false;
        }

        if (isSmallNoisyFragment(metrics)) {
            return false;
        }

        if (isSingleColumnTextBlock(metrics)) {
            return false;
        }

        if (metrics.rowCount() == 1) {
            return isStrongSingleRowTable(metrics);
        }

        if (metrics.score() >= MIN_ACCEPTABLE_SCORE) {
            return true;
        }

        return metrics.keyValueRowCount() >= 2
                && metrics.meaningfulWordCount() >= 4
                && metrics.singleCharacterTokenRatio() <= MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO;
    }

    private boolean isSmallNoisyFragment(QualityMetrics metrics) {
        return metrics.rowCount() <= 2
                && metrics.singleCharacterTokenRatio() > MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO
                && !metrics.hasHeaderTerms()
                && metrics.keyValueRowCount() == 0;
    }

    private boolean isSingleColumnTextBlock(QualityMetrics metrics) {
        return metrics.columnCount() == 1
                && !metrics.hasHeaderTerms();
    }

    private boolean isStrongSingleRowTable(QualityMetrics metrics) {
        return metrics.hasHeaderTerms()
                || metrics.numericValueRowCount() > 0
                || metrics.abbreviationKeyValueRowCount() > 0;
    }

    private record QualityMetrics(
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
            boolean hasHeaderTerms,
            int score
    ) {

        private static QualityMetrics from(TableFragment fragment) {
            MetricAccumulator accumulator = new MetricAccumulator(fragment);

            return accumulator.toMetrics();
        }

        private double singleCharacterTokenRatio() {
            if (tokenCount == 0) {
                return 0;
            }

            return (double) singleCharacterTokenCount / tokenCount;
        }

        private double multiColumnRowRatio() {
            return (double) multiColumnRowCount / rowCount;
        }

        private double longSentenceRowRatio() {
            return (double) longSentenceRowCount / rowCount;
        }
    }

    /**
     * Собирает признаки фрагмента в одном проходе по строкам и ячейкам.
     *
     * <p>Класс оставлен внутренним, потому что эти метрики являются деталями текущей эвристики фильтра,
     * а не самостоятельной частью доменной модели.</p>
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
        private boolean hasHeaderTerms;

        private MetricAccumulator(TableFragment fragment) {
            this.fragment = fragment;
        }

        private QualityMetrics toMetrics() {
            collect();

            return new QualityMetrics(
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
                    hasHeaderTerms,
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

            if (((double) multiColumnRowCount / fragment.rowCount()) >= MIN_MULTI_COLUMN_ROW_RATIO) {
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

            if (singleCharacterTokenRatio() > MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO) {
                result -= 3;
            }

            if (longSentenceRowRatio() > MAX_LONG_SENTENCE_ROW_RATIO
                    && keyValueRowCount < 2
                    && !hasHeaderTerms) {
                result -= 2;
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
