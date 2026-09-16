package com.pdf2tz.backend.application.pdf.model.table;

import java.util.Objects;

/**
 * Ячейка таблицы.
 *
 * <p>Пустой текст допустим: в медицинских инструкциях таблицы могут содержать
 * незаполненные ячейки или объединённые области, которые после извлечения
 * представлены пустыми значениями.</p>
 *
 * @param text очищенный текст ячейки
 */
public record TableCell(
        String text
) {

    public TableCell {
        text = Objects.requireNonNull(text, "Table cell text must not be null")
                .trim();
    }

    /**
     * Проверяет, содержит ли ячейка значимый текст.
     *
     * @return {@code true}, если ячейка пустая после нормализации пробелов
     */
    public boolean isBlank() {
        return text.isBlank();
    }
}
