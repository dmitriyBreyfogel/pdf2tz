package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

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
     * Максимальная доля коротких односимвольных токенов, допустимая для небольшой таблицы.
     */
    private static final double MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO = 0.50;

    /**
     * Минимальный объём текста для оценки повреждённой кодировки.
     * Короткие обозначения и отдельные формулы этим правилом не оцениваются.
     */
    private static final int MIN_ENCODING_SAMPLE_LENGTH = 60;

    /**
     * Минимальная доля букв и цифр среди непробельных символов длинного фрагмента.
     * Если более 40% составляют знаки, текст может быть результатом сломанной карты
     * шрифта. Фрагмент оставляется в тексте, а не объявляется достоверной таблицей.
     */
    private static final double MIN_ALPHANUMERIC_RATIO = 0.60;

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
        TableQualityMetrics metrics = TableQualityMetrics.from(fragment);

        if (hasCorruptedText(fragment)) {
            return false;
        }

        if (metrics.meaningfulWordCount() == 0) {
            return false;
        }

        if (isSmallNoisyFragment(metrics)) {
            return false;
        }

        if (metrics.isTableOfContentsLike()) {
            return false;
        }

        if (isDegenerateMultiColumnFragment(metrics)) {
            return false;
        }

        if (isSparseMultiColumnFragment(metrics)) {
            return false;
        }

        if (isIncompleteHeaderValueFragment(metrics)) {
            return false;
        }

        if (isSparseGridFragment(metrics)) {
            return false;
        }

        if (hasUnsupportedColumn(fragment)) {
            return false;
        }

        if (isSingleColumnTextBlock(metrics)) {
            return false;
        }

        if (metrics.rowCount() == 1) {
            return isStrongSingleRowTable(fragment, metrics);
        }

        if (metrics.score() >= MIN_ACCEPTABLE_SCORE) {
            return true;
        }

        return metrics.keyValueRowCount() >= 2
                && metrics.meaningfulWordCount() >= 4
                && metrics.singleCharacterTokenRatio() <= MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO;
    }

    private boolean isSmallNoisyFragment(TableQualityMetrics metrics) {
        return metrics.rowCount() <= 2
                && metrics.singleCharacterTokenRatio() > MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO
                && !metrics.hasHeaderTerms()
                && metrics.keyValueRowCount() == 0;
    }

    private boolean isSingleColumnTextBlock(TableQualityMetrics metrics) {
        return metrics.columnCount() == 1;
    }

    private boolean isDegenerateMultiColumnFragment(TableQualityMetrics metrics) {
        return metrics.columnCount() >= 2
                && metrics.multiColumnRowCount() == 0;
    }

    private boolean isSparseMultiColumnFragment(TableQualityMetrics metrics) {
        return metrics.columnCount() >= 2
                && metrics.rowCount() >= 3
                && metrics.multiColumnRowRatio() < 0.25
                && metrics.keyValueRowCount() < 2;
    }

    private boolean isIncompleteHeaderValueFragment(TableQualityMetrics metrics) {
        return metrics.columnCount() == 2
                && metrics.rowCount() >= 3
                && metrics.hasHeaderTerms()
                && metrics.multiColumnRowCount() == 1
                && metrics.keyValueRowCount() < 2;
    }

    private boolean isSparseGridFragment(TableQualityMetrics metrics) {
        return metrics.columnCount() >= 3
                && metrics.rowCount() >= 2
                && metrics.filledCellRatio() < 0.60;
    }

    /**
     * Отбрасывает колонки, которые не имеют данных под шапкой, либо представлены
     * единственным обрывком без шапки. Такое извлечение не подтверждает связи колонок:
     * это бывает при потере изображений, объединённых ячейках и захвате номера страницы.
     * Значения остаются в текстовом слое, пустоты не заполняются догадками.
     */
    private boolean hasUnsupportedColumn(TableFragment fragment) {
        if (fragment.rowCount() < 2) {
            return false;
        }
        for (int column = 0; column < fragment.columnCount(); column++) {
            int index = column;
            boolean hasLabel = !fragment.rows().get(0).cells().get(index).isBlank();
            long dataCells = fragment.rows().stream().skip(1)
                    .filter(row -> !row.cells().get(index).isBlank())
                    .count();
            if (hasLabel && dataCells == 0
                    || !hasLabel && fragment.rowCount() >= 3 && dataCells <= 1) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCorruptedText(TableFragment fragment) {
        String text = fragment.rows().stream()
                .flatMap(row -> row.cells().stream())
                .map(TableCell::text)
                .collect(java.util.stream.Collectors.joining());
        long visibleCount = text.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        long alphanumericCount = text.codePoints().filter(Character::isLetterOrDigit).count();
        return visibleCount >= MIN_ENCODING_SAMPLE_LENGTH
                && (double) alphanumericCount / visibleCount < MIN_ALPHANUMERIC_RATIO;
    }

    /**
     * Одна строка допускается только как содержательная пара с числовым значением.
     * Слова из словаря заголовков сами по себе не подтверждают наличие данных.
     */
    private boolean isStrongSingleRowTable(TableFragment fragment, TableQualityMetrics metrics) {
        List<String> cells = fragment.rows().get(0).cells().stream()
                .map(TableCell::text).filter(text -> !text.isBlank()).toList();
        return cells.size() == fragment.columnCount()
                && cells.stream().distinct().count() == cells.size()
                && cells.get(0).codePoints().filter(Character::isLetter).count() >= 3
                && metrics.numericValueRowCount() > 0;
    }
}
