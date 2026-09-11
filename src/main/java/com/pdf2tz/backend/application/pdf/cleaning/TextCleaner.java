package com.pdf2tz.backend.application.pdf.cleaning;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.pdf.model.ExtractedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedPage;
import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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
     * Самостоятельный доменный хвост, который часто попадает в ячейки таблиц как обрывок watermark.
     *
     * <p>Правило применяется к атомарному фрагменту целиком или к отдельному токену внутри строки. Полезный
     * домен без пробела перед точкой, например {@code example.ru}, этим правилом не режется.</p>
     */
    private static final Pattern STANDALONE_DOMAIN_SUFFIX_PATTERN = Pattern.compile(
            "\\.[\\p{L}]{2,}",
            TEXT_PATTERN_FLAGS
    );

    /**
     * Доменный хвост как отдельный токен внутри полезной строки.
     */
    private static final Pattern STANDALONE_DOMAIN_SUFFIX_TOKEN_PATTERN = Pattern.compile(
            "(?<!\\S)\\.[\\p{L}]{2,}(?!\\S)",
            TEXT_PATTERN_FLAGS
    );

    /**
     * Разнесённые пробелами латинские буквы, которые встречаются как остаток диагонального watermark
     * внутри табличных строк.
     */
    private static final Pattern SPACED_ASCII_WATERMARK_PATTERN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])[a-z]{1,3}(?:\\s+[a-z]{1,3}){1,3}(?![\\p{L}\\p{N}])",
            TEXT_PATTERN_FLAGS
    );

    /**
     * Одиночная латинская буква, вклеенная внутрь кириллического слова.
     */
    private static final Pattern ASCII_BETWEEN_CYRILLIC_PATTERN = Pattern.compile(
            "(?<=[\\p{IsCyrillic}])[a-z](?=[\\p{IsCyrillic}])",
            TEXT_PATTERN_FLAGS
    );

    /**
     * Минимальная длина ASCII-фрагмента watermark, который можно удалять внутри табличной ячейки.
     *
     * <p>Табличный режим чуть агрессивнее обычной чистки: Tabula часто вклеивает части диагонального
     * watermark прямо в полезную ячейку. При этом одиночные буквы удалять нельзя, потому что они легко
     * могут быть частью настоящего обозначения, модели или единицы измерения.</p>
     */
    private static final int MIN_TABLE_CELL_ASCII_NOISE_LENGTH = 3;

    /**
     * Максимальная длина короткого ASCII-фрагмента, который рассматривается как обрывок watermark в ячейке.
     */
    private static final int MAX_TABLE_CELL_ASCII_NOISE_LENGTH = 8;

    /**
     * Длина производных фрагментов, которые строятся из повторяющихся ASCII-частей watermark.
     *
     * <p>Например, если в профиле шума есть {@code ravn}, то в ячейке может встретиться только
     * {@code rav}. Поэтому для table-cell режима безопасно добавить трёхбуквенные подпоследовательности,
     * но не уходить в одно- и двухбуквенные совпадения.</p>
     */
    private static final int DERIVED_TABLE_CELL_ASCII_FRAGMENT_LENGTH = 3;

    /**
     * Минимальная длина короткого line-noise токена, который можно удалять из табличной ячейки как
     * самостоятельный фрагмент.
     */
    private static final int MIN_TABLE_CELL_LINE_NOISE_TOKEN_LENGTH = 3;

    /**
     * Максимальная длина короткого line-noise токена для точечной table-cell чистки.
     */
    private static final int MAX_TABLE_CELL_LINE_NOISE_TOKEN_LENGTH = 8;

    /**
     * Максимальная длина ячейки, которую можно целиком признать составленным обрывком watermark.
     */
    private static final int MAX_COMPOSED_TABLE_CELL_NOISE_LENGTH = 12;

    /**
     * Минимальное количество латинских букв внутри кириллических слов одной ячейки, после которого
     * фрагмент считается следом watermark, а не единичной OCR-ошибкой.
     */
    private static final int MIN_EMBEDDED_ASCII_NOISE_IN_CYRILLIC_WORD = 2;

    /**
     * Короткие ASCII-значения, которые часто являются полезными значениями таблиц и не должны удаляться
     * даже при совпадении с похожим шумовым фрагментом.
     */
    private static final Set<String> PROTECTED_TABLE_CELL_ASCII_TOKENS = Set.of(
            "no",
            "on",
            "off",
            "min",
            "max",
            "din",
            "dc",
            "ac",
            "vga",
            "usb",
            "rf",
            "id",
            "type",
            "name",
            "value",
            "ml",
            "mg",
            "mcg",
            "kg",
            "mm",
            "cm",
            "sec"
    );

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

    /**
     * Очищает самостоятельный текстовый фрагмент от строкового и inline-шума.
     *
     * <p>Метод предназначен для текста, который имеет собственные границы:
     * строка документа, ячейка таблицы или другое атомарное значение. Если весь
     * фрагмент совпадает со строковым шумом из {@link DocumentNoiseProfile#lineNoise()},
     * возвращается пустая строка. Если фрагмент не является шумом целиком,
     * внутри него удаляются только inline-фрагменты.</p>
     *
     * @param text исходный текстовый фрагмент
     * @param noiseProfile профиль служебного шума конкретного документа
     * @return очищенный текстовый фрагмент
     */
    public String cleanTextFragment(
            String text,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(noiseProfile, "Noise profile must not be null");

        Set<String> lineNoise = normalizeNoiseSet(noiseProfile.lineNoise());
        List<String> inlineNoise = normalizeInlineNoise(noiseProfile.inlineNoise());

        return cleanTextFragment(text, lineNoise, inlineNoise);
    }

    /**
     * Очищает текст табличной ячейки с учётом особенностей Tabula-извлечения.
     *
     * <p>Метод сначала применяет обычную чистку текстового фрагмента, а затем удаляет короткие
     * ASCII-обрывки watermark, которые Tabula часто приклеивает прямо к табличным данным. Эта логика
     * намеренно отделена от {@link #cleanTextFragment(String, DocumentNoiseProfile)}, чтобы не делать
     * обычный текст документа слишком агрессивным.</p>
     *
     * @param text исходный текст ячейки таблицы
     * @param noiseProfile профиль служебного шума конкретного документа
     * @return очищенный текст ячейки таблицы
     */
    public String cleanTableCellText(
            String text,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(noiseProfile, "Noise profile must not be null");

        Set<String> lineNoise = normalizeNoiseSet(noiseProfile.lineNoise());
        List<String> inlineNoise = normalizeInlineNoise(noiseProfile.inlineNoise());
        String cleanedText = cleanTextFragment(text, lineNoise, inlineNoise);

        if (cleanedText.isBlank()) {
            return "";
        }

        List<String> tableCellLineNoise = tableCellLineNoise(lineNoise);
        boolean hasDomainWatermarkNoise = hasDomainWatermarkNoise(lineNoise, inlineNoise);

        cleanedText = removeTableCellLineNoise(cleanedText, tableCellLineNoise);

        for (String noise : tableCellAsciiNoise(lineNoise, inlineNoise)) {
            cleanedText = removeTableCellAsciiNoise(cleanedText, noise);
        }

        cleanedText = removeNumericAttachedAsciiNoise(
                cleanedText,
                tableCellNumericSuffixAsciiNoise(lineNoise, inlineNoise)
        );
        cleanedText = removePunctuatedDomainNoise(cleanedText, hasDomainWatermarkNoise);
        cleanedText = removeSpacedAsciiWatermarkNoise(cleanedText, hasDomainWatermarkNoise);
        cleanedText = removeEmbeddedAsciiNoiseInCyrillicWords(cleanedText, hasDomainWatermarkNoise);
        cleanedText = normalizeLine(cleanedText);

        if (isComposedTableCellLineNoise(cleanedText, tableCellLineNoise)) {
            return "";
        }

        return cleanedText;
    }

    private CleanedPage cleanPage(
            ExtractedPage page,
            Set<String> lineNoise,
            List<String> inlineNoise
    ) {
        String cleanedText = safeText(page.text())
                .lines()
                .map(line -> cleanTextFragment(line, lineNoise, inlineNoise))
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(LINE_SEPARATOR));

        return new CleanedPage(
                page.pageNumber(),
                cleanedText
        );
    }

    private String cleanTextFragment(
            String text,
            Set<String> lineNoise,
            List<String> inlineNoise
    ) {
        String normalizedText = normalizeLine(text);

        if (normalizedText.isBlank() || isLineNoise(normalizedText, lineNoise)) {
            return "";
        }

        if (isStandaloneDomainSuffix(normalizedText)) {
            return "";
        }

        return cleanInlineText(normalizedText, inlineNoise);
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

        result = removeStandaloneDomainSuffixTokens(result);

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

    /**
     * Формирует набор коротких ASCII-фрагментов, которые можно дополнительно удалять только из ячеек таблиц.
     *
     * <p>Источник этих фрагментов - уже найденный профиль шума документа. Класс не знает конкретный текст
     * watermark заранее: он лишь выводит компактные ASCII-части и короткие производные от повторяющихся
     * фрагментов, потому что Tabula может порезать один и тот же watermark иначе, чем Tika.</p>
     */
    private List<String> tableCellAsciiNoise(
            Set<String> lineNoise,
            List<String> inlineNoise
    ) {
        Set<String> result = new HashSet<>();

        lineNoise.forEach(noise -> addTableCellAsciiNoiseVariants(noise, result));
        inlineNoise.forEach(noise -> addTableCellAsciiNoiseVariants(noise, result));

        if (hasDomainWatermarkNoise(lineNoise, inlineNoise)) {
            result.add("gov");
        }

        return result.stream()
                .filter(this::isSafeTableCellAsciiNoise)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private List<String> tableCellNumericSuffixAsciiNoise(
            Set<String> lineNoise,
            List<String> inlineNoise
    ) {
        Set<String> result = new HashSet<>();

        lineNoise.forEach(noise -> addNumericSuffixAsciiNoise(noise, result));
        inlineNoise.forEach(noise -> addNumericSuffixAsciiNoise(noise, result));

        if (hasDomainWatermarkNoise(lineNoise, inlineNoise)) {
            result.add("or");
        }

        return result.stream()
                .filter(noise -> noise.length() == 2)
                .filter(noise -> !PROTECTED_TABLE_CELL_ASCII_TOKENS.contains(noise))
                .sorted()
                .toList();
    }

    private void addNumericSuffixAsciiNoise(
            String noise,
            Set<String> result
    ) {
        String compact = compactAsciiLetters(noise);

        if (compact.length() >= 4) {
            result.add(compact.substring(compact.length() - 2));
        }
    }

    private void addTableCellAsciiNoiseVariants(
            String noise,
            Set<String> result
    ) {
        String compact = compactAsciiLetters(noise);

        if (!isPotentialTableCellAsciiNoise(compact)) {
            return;
        }

        result.add(compact);
        addDerivedAsciiFragments(compact, result);
    }

    private void addDerivedAsciiFragments(
            String compact,
            Set<String> result
    ) {
        if (compact.length() < DERIVED_TABLE_CELL_ASCII_FRAGMENT_LENGTH) {
            return;
        }

        for (
                int startIndex = 0;
                startIndex <= compact.length() - DERIVED_TABLE_CELL_ASCII_FRAGMENT_LENGTH;
                startIndex++
        ) {
            result.add(compact.substring(
                    startIndex,
                    startIndex + DERIVED_TABLE_CELL_ASCII_FRAGMENT_LENGTH
            ));
        }
    }

    private List<String> tableCellLineNoise(Set<String> lineNoise) {
        return lineNoise.stream()
                .filter(this::isPotentialTableCellLineNoise)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private boolean isPotentialTableCellLineNoise(String noise) {
        int length = compactLetters(noise).length();

        return length >= MIN_TABLE_CELL_LINE_NOISE_TOKEN_LENGTH
                && length <= MAX_TABLE_CELL_LINE_NOISE_TOKEN_LENGTH
                && !compactLetters(noise).isBlank()
                && !containsDigit(noise)
                && !PROTECTED_TABLE_CELL_ASCII_TOKENS.contains(compactAsciiLetters(noise));
    }

    private boolean hasDomainWatermarkNoise(
            Set<String> lineNoise,
            List<String> inlineNoise
    ) {
        return lineNoise.stream().anyMatch(this::isDomainNoiseFragment)
                || inlineNoise.stream().anyMatch(this::isDomainNoiseFragment);
    }

    private boolean isDomainNoiseFragment(String noise) {
        return noise.contains(".")
                && compactAsciiLetters(noise).length() >= MIN_TABLE_CELL_ASCII_NOISE_LENGTH;
    }

    private boolean isPotentialTableCellAsciiNoise(String compact) {
        return compact.length() >= MIN_TABLE_CELL_ASCII_NOISE_LENGTH
                && compact.length() <= MAX_TABLE_CELL_ASCII_NOISE_LENGTH
                && compact.chars().allMatch(this::isAsciiLowercaseLetter);
    }

    private boolean isSafeTableCellAsciiNoise(String noise) {
        return isPotentialTableCellAsciiNoise(noise)
                && !PROTECTED_TABLE_CELL_ASCII_TOKENS.contains(noise);
    }

    private String removeTableCellAsciiNoise(
            String text,
            String noise
    ) {
        Pattern pattern = tableCellAsciiNoisePattern(noise);

        return pattern.matcher(text).replaceAll(" ");
    }

    private Pattern tableCellAsciiNoisePattern(String noise) {
        String expression = noise.chars()
                .mapToObj(character -> Pattern.quote(Character.toString(character)))
                .collect(Collectors.joining("[\\s.]*"));

        return Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + expression + "(?![\\p{L}\\p{N}])",
                TEXT_PATTERN_FLAGS
        );
    }

    private String removeNumericAttachedAsciiNoise(
            String text,
            List<String> noiseFragments
    ) {
        String result = text;

        for (String noise : noiseFragments) {
            Pattern pattern = numericAttachedAsciiNoisePattern(noise);

            result = pattern.matcher(result).replaceAll(" ");
        }

        return result;
    }

    private Pattern numericAttachedAsciiNoisePattern(String noise) {
        String expression = noise.chars()
                .mapToObj(character -> Pattern.quote(Character.toString(character)))
                .collect(Collectors.joining("[\\s.]*"));

        return Pattern.compile(
                "(?<=\\d)" + expression + "(?![\\p{L}\\p{N}])",
                TEXT_PATTERN_FLAGS
        );
    }

    private String removePunctuatedDomainNoise(
            String text,
            boolean hasDomainWatermarkNoise
    ) {
        if (!hasDomainWatermarkNoise) {
            return text;
        }

        return Pattern.compile(
                        "(?<![\\p{L}\\p{N}])(?:or|gov)\\.(?![\\p{L}\\p{N}])|(?<!\\S)\\.[a-z](?!\\S)",
                        TEXT_PATTERN_FLAGS
                )
                .matcher(text)
                .replaceAll(" ");
    }

    private String removeSpacedAsciiWatermarkNoise(
            String text,
            boolean hasDomainWatermarkNoise
    ) {
        if (!hasDomainWatermarkNoise) {
            return text;
        }

        return SPACED_ASCII_WATERMARK_PATTERN.matcher(text)
                .replaceAll(" ");
    }

    private String removeEmbeddedAsciiNoiseInCyrillicWords(
            String text,
            boolean hasDomainWatermarkNoise
    ) {
        if (!hasDomainWatermarkNoise) {
            return text;
        }

        long embeddedAsciiCount = ASCII_BETWEEN_CYRILLIC_PATTERN.matcher(text)
                .results()
                .count();

        if (embeddedAsciiCount < MIN_EMBEDDED_ASCII_NOISE_IN_CYRILLIC_WORD) {
            return text;
        }

        return ASCII_BETWEEN_CYRILLIC_PATTERN.matcher(text)
                .replaceAll("");
    }

    private String removeTableCellLineNoise(
            String text,
            List<String> lineNoise
    ) {
        String result = text;

        for (String noise : lineNoise) {
            Pattern pattern = tableCellLineNoisePattern(noise);

            result = pattern.matcher(result).replaceAll(" ");
        }

        return result;
    }

    private Pattern tableCellLineNoisePattern(String noise) {
        String expression = Arrays.stream(noise.split("\\s+"))
                .map(Pattern::quote)
                .collect(Collectors.joining("\\s+"));

        return Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + expression + "(?![\\p{L}\\p{N}])",
                TEXT_PATTERN_FLAGS
        );
    }

    private boolean isComposedTableCellLineNoise(
            String text,
            List<String> lineNoise
    ) {
        String compactText = compactLetters(text);

        if (compactText.length() < MIN_TABLE_CELL_LINE_NOISE_TOKEN_LENGTH
                || compactText.length() > MAX_COMPOSED_TABLE_CELL_NOISE_LENGTH) {
            return false;
        }

        return lineNoise.stream()
                .map(this::compactLetters)
                .filter(noise -> noise.length() >= MIN_TABLE_CELL_LINE_NOISE_TOKEN_LENGTH)
                .anyMatch(noise -> compactText.contains(noise) || noise.contains(compactText));
    }

    private String compactLetters(String text) {
        return normalizeForCompare(text).codePoints()
                .filter(Character::isLetter)
                .collect(
                        StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        (builder, other) -> builder.append(other)
                )
                .toString();
    }

    private String compactAsciiLetters(String text) {
        return normalizeForCompare(text).chars()
                .filter(this::isAsciiLetter)
                .collect(
                        StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        (builder, other) -> builder.append(other)
                )
                .toString();
    }

    private boolean isAsciiLetter(int character) {
        return isAsciiLowercaseLetter(character)
                || (character >= 'A' && character <= 'Z');
    }

    private boolean isAsciiLowercaseLetter(int character) {
        return character >= 'a' && character <= 'z';
    }

    private boolean containsDigit(String text) {
        return text.codePoints().anyMatch(Character::isDigit);
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

    private boolean isStandaloneDomainSuffix(String text) {
        return STANDALONE_DOMAIN_SUFFIX_PATTERN.matcher(text).matches();
    }

    private String removeStandaloneDomainSuffixTokens(String text) {
        return STANDALONE_DOMAIN_SUFFIX_TOKEN_PATTERN.matcher(text).replaceAll(" ");
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
