package com.pdf2tz.backend.application.pdf.cleaning;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Очищает извлечённый текст от служебного шума PDF-документа.
 *
 * <p>Класс применяет уже построенный {@link DocumentNoiseProfile}: удаляет
 * целые шумовые строки, вырезает шумовые фрагменты внутри строк и приводит
 * пробельные символы к единому виду. Самостоятельно шум не определяет.</p>
 */
@Component
public class TextCleaner {

    /**
     * Единый разделитель строк в очищенном документе.
     * Используется вместо системного разделителя, чтобы результат не зависел от ОС.
     */
    private static final String LINE_SEPARATOR = "\n";

    /**
     * Невидимые Unicode-символы, которые могут попадать в текст из PDF-слоя.
     */
    private static final Pattern ZERO_WIDTH_CHARACTERS_PATTERN = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");

    /**
     * Последовательность пробельных символов, которую нужно заменить одним пробелом.
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Невидимый мягкий перенос, который может ломать сравнение строк.
     */
    private static final String SOFT_HYPHEN = "\u00AD";

    /**
     * Флаги регулярных выражений для поиска текста без учёта регистра
     * и с корректной поддержкой Unicode-символов.
     */
    private static final int TEXT_PATTERN_FLAGS = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    /**
     * Очищает все страницы документа от служебного шума.
     *
     * <p>Метод заранее нормализует профиль шума и переиспользует его для
     * всех страниц. Это дешевле и понятнее, чем пересобирать профиль для
     * каждой страницы отдельно.</p>
     *
     * @param document сырой постраничный текст PDF-документа
     * @param noiseProfile профиль служебного шума конкретного документа
     * @return документ с очищенными страницами
     */
    public CleanedDocument cleanDocument(
            ExtractedDocument document,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(document, "Document must not be null");
        Objects.requireNonNull(noiseProfile, "Noise profile must not be null");

        Set<String> lineNoise = normalizeNoiseSet(noiseProfile.lineNoise());
        List<String> inlineNoise = normalizeInlineNoise(noiseProfile.inlineNoise());

        List<CleanedPage> pages = document.pages().stream()
                .map(page -> cleanPage(page, lineNoise, inlineNoise))
                .toList();

        return new CleanedDocument(pages);
    }

    /**
     * Очищает одну страницу документа от служебного шума.
     *
     * <p>Строки, совпавшие с {@link DocumentNoiseProfile#lineNoise()},
     * удаляются целиком. В оставшихся строках удаляются inline-фрагменты
     * из {@link DocumentNoiseProfile#inlineNoise()}.</p>
     *
     * @param page сырая страница PDF-документа
     * @param noiseProfile профиль служебного шума конкретного документа
     * @return страница с очищенным текстом и исходным номером страницы
     */
    public CleanedPage cleanPage(
            ExtractedPage page,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(page, "Page must not be null");
        Objects.requireNonNull(noiseProfile, "Noise profile must not be null");

        Set<String> lineNoise = normalizeNoiseSet(noiseProfile.lineNoise());
        List<String> inlineNoise = normalizeInlineNoise(noiseProfile.inlineNoise());

        return cleanPage(page, lineNoise, inlineNoise);
    }

    /**
     * Очищает строку или текст ячейки таблицы от inline-шума.
     *
     * <p>Метод предназначен для переиспользования не только в очистке
     * Tika-текста, но и в будущей нормализации таблиц. Поэтому он не знает
     * ничего о страницах, строках таблицы или структуре документа.</p>
     *
     * @param text исходный текст строки или ячейки
     * @param noiseProfile профиль служебного шума конкретного документа
     * @return очищенный inline-текст
     */
    public String cleanInlineText(
            String text,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(noiseProfile, "Noise profile must not be null");

        List<String> inlineNoise = normalizeInlineNoise(noiseProfile.inlineNoise());

        return cleanInlineText(text, inlineNoise);
    }

    private CleanedPage cleanPage(
            ExtractedPage page,
            Set<String> lineNoise,
            List<String> inlineNoise
    ) {
        String cleanedText = safeText(page.text())
                .lines()
                .map(this::normalizeLine)
                .filter(line -> !line.isBlank())
                .filter(line -> !isLineNoise(line, lineNoise))
                .map(line -> cleanInlineText(line, inlineNoise))
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(LINE_SEPARATOR));

        return new CleanedPage(
                page.pageNumber(),
                cleanedText
        );
    }

    /**
     * Удаляет inline-шум из текста, сохраняя исходный регистр полезного текста.
     *
     * <p>Шумовые фрагменты удаляются от самых длинных к самым коротким.
     * Это снижает риск частичной очистки, когда короткий фрагмент является
     * частью более длинного шумового выражения.</p>
     */
    private String cleanInlineText(
            String text,
            List<String> inlineNoise
    ) {
        String result = normalizeLine(text);

        for (String noise : inlineNoise) {
            result = removeInlineNoise(result, noise);
        }

        return normalizeLine(result);
    }

    /**
     * Удаляет один шумовой фрагмент из текста.
     *
     * <p>Фрагмент превращается в регулярное выражение с гибкими пробелами
     * между словами. Это позволяет удалить шум даже тогда, когда Tika или OCR
     * по-разному расставили пробелы внутри одного и того же водяного знака.</p>
     */
    private String removeInlineNoise(
            String text,
            String noise
    ) {
        if (noise.isBlank()) {
            return text;
        }

        Pattern pattern = inlineNoisePattern(noise);

        return pattern.matcher(text).replaceAll(" ");
    }

    private Pattern inlineNoisePattern(String noise) {
        String expression = Arrays.stream(noise.split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));

        return Pattern.compile(expression, TEXT_PATTERN_FLAGS);
    }

    private Set<String> normalizeNoiseSet(Set<String> noise) {
        return noise.stream()
                .map(this::normalizeForCompare)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private List<String> normalizeInlineNoise(Set<String> noise) {
        return normalizeNoiseSet(noise).stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private boolean isLineNoise(
            String line,
            Set<String> lineNoise
    ) {
        return lineNoise.contains(normalizeForCompare(line));
    }

    private String normalizeLine(String text) {
        return WHITESPACE_PATTERN.matcher(removeInvisibleCharacters(safeText(text)))
                .replaceAll(" ")
                .trim();
    }

    private String normalizeForCompare(String text) {
        return normalizeLine(text)
                .replace('ё', 'е')
                .toLowerCase(Locale.ROOT);
    }

    private String removeInvisibleCharacters(String text) {
        String withoutSoftHyphen = text.replace(SOFT_HYPHEN, "");

        return ZERO_WIDTH_CHARACTERS_PATTERN.matcher(withoutSoftHyphen)
                .replaceAll("");
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}
