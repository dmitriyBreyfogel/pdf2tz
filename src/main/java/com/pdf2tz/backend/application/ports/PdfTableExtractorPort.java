package com.pdf2tz.backend.application.ports;

import com.pdf2tz.backend.application.pdf.model.table.TableCandidate;

import java.util.List;

/**
 * Порт извлечения сырых табличных кандидатов из PDF-документа.
 *
 * <p>Application-слой не знает, какая конкретная библиотека используется
 * для поиска таблиц: Tabula, PDFBox-адаптер или другой инструмент. Реализация
 * порта должна только вернуть кандидаты на таблицы. Очистка, выбор лучших
 * кандидатов, нормализация и склейка фрагментов выполняются отдельными
 * application-сервисами.</p>
 */
public interface PdfTableExtractorPort {

    /**
     * Извлекает сырые табличные кандидаты из PDF-файла.
     *
     * @param content байтовое представление PDF-файла
     * @return найденные кандидаты, упорядоченные по страницам и позиции чтения
     */
    List<TableCandidate> extract(byte[] content);
}
