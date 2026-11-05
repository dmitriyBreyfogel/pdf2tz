package com.pdf2tz.backend.application.pdf.table;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Единое сравнимое представление текста из нативного слоя PDF и ячеек таблиц.
 * Удаляет только различия в пробелах и пунктуации, не восстанавливая слова.
 */
public final class TableTextMatch {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private TableTextMatch() {
    }

    /**
     * Сравнивает строки при разной расстановке пробелов и знаков в PDF.
     *
     * @param text исходный текст строки или ячейки
     * @return буквы и цифры в нижнем регистре без разделителей
     */
    public static String compact(String text) {
        String normalized = Objects.requireNonNull(text, "Table text must not be null")
                .replace('ё', 'е')
                .toLowerCase(Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(normalized).replaceAll("");
    }
}
