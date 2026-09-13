package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
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

    private boolean isSmallNoisyFragment(TableQualityMetrics metrics) {
        return metrics.rowCount() <= 2
                && metrics.singleCharacterTokenRatio() > MAX_SMALL_FRAGMENT_SINGLE_CHARACTER_TOKEN_RATIO
                && !metrics.hasHeaderTerms()
                && metrics.keyValueRowCount() == 0;
    }

    private boolean isSingleColumnTextBlock(TableQualityMetrics metrics) {
        return metrics.columnCount() == 1
                && !metrics.hasHeaderTerms();
    }

    private boolean isStrongSingleRowTable(TableQualityMetrics metrics) {
        return metrics.hasHeaderTerms()
                || metrics.numericValueRowCount() > 0
                || metrics.abbreviationKeyValueRowCount() > 0;
    }
}
