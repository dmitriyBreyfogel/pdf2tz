package com.pdf2tz.backend.application.pdf.table;

import com.pdf2tz.backend.application.pdf.model.cleaning.DocumentNoiseProfile;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.ports.PdfTableExtractorPort;

import java.util.List;
import java.util.Objects;

/**
 * Координирует полный application-flow обработки таблиц PDF-документа.
 *
 * <p>Сервис связывает внешний извлекатель таблиц с внутренними этапами
 * обработки: выбором пригодных кандидатов, нормализацией фрагментов и сборкой
 * целостных таблиц. На этом уровне не должно быть деталей конкретной
 * PDF-библиотеки: Tabula, PDFBox или другой инструмент скрываются за
 * {@link PdfTableExtractorPort}.</p>
 */
public class PdfTableParsingService {

    private final PdfTableExtractorPort tableExtractorPort;
    private final TableCandidateSelector tableCandidateSelector;
    private final TableNormalizer tableNormalizer;
    private final TableAssembler tableAssembler;

    public PdfTableParsingService(
            PdfTableExtractorPort tableExtractorPort,
            TableCandidateSelector tableCandidateSelector,
            TableNormalizer tableNormalizer,
            TableAssembler tableAssembler
    ) {
        this.tableExtractorPort = Objects.requireNonNull(
                tableExtractorPort,
                "PDF table extractor port must not be null"
        );
        this.tableCandidateSelector = Objects.requireNonNull(
                tableCandidateSelector,
                "Table candidate selector must not be null"
        );
        this.tableNormalizer = Objects.requireNonNull(
                tableNormalizer,
                "Table normalizer must not be null"
        );
        this.tableAssembler = Objects.requireNonNull(
                tableAssembler,
                "Table assembler must not be null"
        );
    }

    /**
     * Извлекает из PDF-документа целостные очищенные таблицы.
     *
     * <p>Метод ожидает уже построенный профиль служебного шума документа.
     * Это важно: один и тот же {@link DocumentNoiseProfile} должен применяться
     * и к обычному тексту, и к содержимому табличных ячеек, чтобы watermark-и
     * и повторяющиеся служебные фрагменты чистились единообразно.</p>
     *
     * @param content байтовое представление PDF-файла
     * @param noiseProfile профиль служебного шума документа
     * @return целостные таблицы документа
     */
    public List<ParsedTable> parseTables(
            byte[] content,
            DocumentNoiseProfile noiseProfile
    ) {
        Objects.requireNonNull(content, "PDF content must not be null");
        Objects.requireNonNull(noiseProfile, "Document noise profile must not be null");

        List<TableCandidate> candidates = tableExtractorPort.extract(content);
        List<TableCandidate> selectedCandidates = tableCandidateSelector.select(candidates);
        List<TableFragment> fragments = tableNormalizer.normalize(selectedCandidates, noiseProfile);

        return tableAssembler.assemble(fragments);
    }
}
