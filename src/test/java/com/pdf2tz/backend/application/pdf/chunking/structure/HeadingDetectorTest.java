package com.pdf2tz.backend.application.pdf.chunking.structure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadingDetectorTest {

    private final HeadingDetector detector = new HeadingDetector();

    @Test
    void acceptsStableDecimalHierarchyAndRejectsMeasurementAndInstructions() {
        List<SourceLine> lines = lines(
                "1 НАЗНАЧЕНИЕ",
                "Описание изделия.",
                "1.1 Область применения",
                "Текст раздела.",
                "1.5 mg/ml",
                "1) нажмите кнопку включения.",
                "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"
        );

        List<HeadingCandidate> headings = detector.detect(lines);

        assertEquals(
                List.of("1 НАЗНАЧЕНИЕ", "1.1 Область применения", "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"),
                headings.stream().map(candidate -> candidate.sourceLine().text()).toList()
        );
    }

    @Test
    void acceptsRepeatedUppercaseSections() {
        List<HeadingCandidate> headings = detector.detect(lines(
                "ЭКСПЛУАТАЦИЯ",
                "Подготовьте изделие.",
                "ТЕХНИЧЕСКОЕ ОБСЛУЖИВАНИЕ",
                "Очистите изделие."
        ));

        assertEquals(2, headings.size());
        assertTrue(headings.stream().allMatch(candidate ->
                candidate.scheme() == HeadingScheme.UNNUMBERED_UPPERCASE
        ));
    }

    @Test
    void ignoresContentsEntriesAndCaptions() {
        List<HeadingCandidate> headings = detector.detect(lines(
                "СОДЕРЖАНИЕ",
                "1 НАЗНАЧЕНИЕ ........ 3",
                "1.1 ОБЛАСТЬ ПРИМЕНЕНИЯ .... 4",
                "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ .... 8",
                "3 ЭКСПЛУАТАЦИЯ .... 12",
                "Рисунок 1. Общий вид изделия",
                "Таблица 2. Основные характеристики"
        ));

        assertTrue(headings.isEmpty());
    }

    @Test
    void doesNotReplaceEstablishedNumberedHierarchyWithUncataloguedFigureLabels() {
        List<SourceLine> lines = new java.util.ArrayList<>();
        int order = 0;
        for (int section = 1; section <= 4; section++) {
            for (int subsection = 1; subsection <= 3; subsection++) {
                lines.add(new SourceLine(section, 0, subsection, order++,
                        section + "." + subsection + " Раздел прибора"));
            }
        }
        lines.add(new SourceLine(5, 0, 0, order++, "KARL STORZ ENDOSKOPE"));
        lines.add(new SourceLine(5, 0, 1, order++, "QQQDI"));
        lines.add(new SourceLine(5, 0, 2, order, "POWER Cut"));

        List<HeadingCandidate> headings = detector.detect(lines);

        assertEquals(12, headings.size());
        assertTrue(headings.stream().allMatch(candidate ->
                candidate.scheme() == HeadingScheme.DECIMAL));
    }

    @Test
    void usesContentsAsCatalogForTitleCaseHeadingsWithoutTreatingContentsAsStructure() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "Содержание"),
                new SourceLine(1, 0, 1, 1, "Эксплуатация ........................................ 97"),
                new SourceLine(1, 0, 2, 2, "Установка параметров расхода свежего газа ........ 98"),
                new SourceLine(1, 0, 3, 3, "Анестезия с низким потоком ......................... 102"),
                new SourceLine(1, 0, 4, 4, "Решение проблем ...................................... 165"),
                new SourceLine(1, 0, 5, 5, "Страница режима ожидания после запуска ............ 98"),
                new SourceLine(97, 0, 0, 6, "Эксплуатация"),
                new SourceLine(98, 0, 2, 7, "Страница режима ожидания после запуска")
        );

        List<HeadingCandidate> headings = detector.detect(lines);

        assertEquals(
                List.of("Эксплуатация", "Страница режима ожидания после запуска"),
                headings.stream().map(candidate -> candidate.sourceLine().text()).toList()
        );
    }

    @Test
    void matchesCatalogTitleInPageHeadingWithProductPrefixOrSuffix() {
        List<SourceLine> lines = List.of(
                new SourceLine(3, 0, 0, 0, "Содержание"),
                new SourceLine(3, 0, 1, 1, "Перфузор компакт С / Обзор�������� 4"),
                new SourceLine(3, 0, 2, 2, "Специальные функции�������� 7"),
                new SourceLine(3, 0, 3, 3, "Совместимые шприцы�������� 12"),
                new SourceLine(3, 0, 4, 4, "Технические характеристики�������� 13"),
                new SourceLine(4, 0, 1, 5, "Обзор Perfusor Compact® S"),
                new SourceLine(13, 0, 1, 6, "Perfusor Compact® SТехнические характеристики")
        );

        assertEquals(
                List.of("Обзор Perfusor Compact® S",
                        "Perfusor Compact® SТехнические характеристики"),
                detector.detect(lines).stream()
                        .map(candidate -> candidate.sourceLine().text())
                        .toList()
        );
    }

    @Test
    void doesNotInferMergedContentsEntriesAsHeadings() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "Содержание"),
                new SourceLine(1, 0, 1, 1, "Работа с прибором ........ 2"),
                new SourceLine(1, 0, 2, 2, "Особые функции ........ 3"),
                new SourceLine(1, 0, 3, 3, "Совместимые шприцы ........ 4"),
                new SourceLine(1, 0, 4, 4, "Принадлежности Подключаемые устройства ........ 5"),
                new SourceLine(1, 0, 5, 5,
                        "Режим вентиляции SIMV/PS 113Подтверждение настроек ........ 6"),
                new SourceLine(2, 0, 0, 6, "2"),
                new SourceLine(3, 0, 0, 7, "Особые функции"),
                new SourceLine(4, 0, 0, 8, "Совместимые шприцы"),
                new SourceLine(5, 0, 0, 9, "5"),
                new SourceLine(6, 0, 0, 10, "6")
        );

        assertEquals(
                List.of("Работа с прибором", "Особые функции", "Совместимые шприцы"),
                detector.detect(lines).stream()
                        .map(HeadingCandidate::headingText)
                        .toList()
        );
    }

    @Test
    void keepsTwoCatalogHeadingsMergedIntoOneSourceLineAsBody() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "Содержание"),
                new SourceLine(1, 0, 1, 1, "Использование ........ 7"),
                new SourceLine(1, 0, 2, 2,
                        "Принадлежности ........ 9Подключаемые устройства ........ 9"),
                new SourceLine(1, 0, 3, 3, "Обслуживание ........ 12"),
                new SourceLine(1, 0, 4, 4, "Утилизация ........ 15"),
                new SourceLine(9, 0, 2, 5, "Принадлежности Подключаемые устройства")
        );

        assertTrue(detector.detect(lines).stream().noneMatch(candidate ->
                candidate.sourceLine().pageNumber() == 9));
    }

    @Test
    void requiresNearbyLetteredSectionsInsteadOfUnrelatedWarningsAndCompanyNames() {
        List<SourceLine> unrelated = List.of(
                new SourceLine(44, 0, 0, 0, "A. Предупреждение: При коротком замыкании"),
                new SourceLine(92, 0, 0, 1,
                        "B. Braun Medical AG HellDur H plus N 1 .2 . 3Stabimed")
        );
        assertTrue(detector.detect(unrelated).isEmpty());

        List<SourceLine> sections = List.of(
                new SourceLine(2, 0, 0, 0, "A. Назначение"),
                new SourceLine(3, 0, 0, 1, "B. Обслуживание")
        );
        assertEquals(List.of("A. Назначение", "B. Обслуживание"),
                detector.detect(sections).stream()
                        .map(candidate -> candidate.sourceLine().text())
                        .toList());
    }

    @Test
    void rejectsRepeatedWarningLabelsAndPageHeaders() {
        List<SourceLine> lines = List.of(
                new SourceLine(1, 0, 0, 0, "1 Руководство по эксплуатации Fabius plus ПО 3.n"),
                new SourceLine(1, 0, 1, 1, "ПРЕДУПРЕЖДЕНИЕ"),
                new SourceLine(1, 0, 2, 2, "Описание."),
                new SourceLine(2, 0, 0, 3, "2 Руководство по эксплуатации Fabius plus ПО 3.n"),
                new SourceLine(2, 0, 1, 4, "ПРЕДУПРЕЖДЕНИЕ"),
                new SourceLine(2, 0, 2, 5, "Описание."),
                new SourceLine(3, 0, 0, 6, "3 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"),
                new SourceLine(3, 0, 1, 7, "Параметры.")
        );

        List<HeadingCandidate> headings = detector.detect(lines);

        assertEquals(
                List.of("3 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"),
                headings.stream().map(candidate -> candidate.sourceLine().text()).toList()
        );
    }

    @Test
    void rejectsRepeatedStatusLabelsEvenWhenTheyAppearAsDiagramLegend() {
        List<HeadingCandidate> headings = detector.detect(List.of(
                new SourceLine(96, 0, 0, 0, "Начало работы"),
                new SourceLine(96, 0, 1, 1, "– РАБОТОСПОСОБНА"),
                new SourceLine(96, 0, 2, 2, "– УСЛОВНО РАБОТОСПОСОБНА"),
                new SourceLine(96, 0, 3, 3, "– НЕ РАБОТОСПОСОБНА"),
                new SourceLine(96, 0, 4, 4, "РАБОТОСПОСОБНА"),
                new SourceLine(96, 0, 5, 5, "Устройство готово к работе."),
                new SourceLine(96, 0, 6, 6, "УСЛОВНО РАБОТОСПОСОБНА"),
                new SourceLine(96, 0, 7, 7, "Обнаружена неисправность."),
                new SourceLine(96, 0, 8, 8, "НЕ РАБОТОСПОСОБНА"),
                new SourceLine(96, 0, 9, 9, "Обнаружена серьезная неисправность.")
        ));

        assertTrue(headings.isEmpty());
    }

    @Test
    void keepsNumberedInstructionsAddressesAndCompanyNamesOutOfSectionPaths() {
        List<HeadingCandidate> headings = detector.detect(lines(
                "1 НАЗНАЧЕНИЕ",
                "1 Извлечь предохранитель аккумуляторной батареи из упаковки",
                "0 Инъекционный порт, безыгольный",
                "2 Использовать до",
                "20 капель в миллилитре жидкости",
                "34209 Melsungen",
                "B. Braun Melsungen AG",
                "В. Braun Melsungen AG",
                "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"
        ));

        assertEquals(
                List.of("1 НАЗНАЧЕНИЕ", "2 ТЕХНИЧЕСКИЕ ХАРАКТЕРИСТИКИ"),
                headings.stream().map(candidate -> candidate.sourceLine().text()).toList()
        );
    }

    private List<SourceLine> lines(String... values) {
        return java.util.stream.IntStream.range(0, values.length)
                .mapToObj(index -> new SourceLine(1, 0, index, index, values[index]))
                .toList();
    }
}
