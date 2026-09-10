package com.pdf2tz.backend.api.pdf.mapper;

import com.pdf2tz.backend.api.pdf.dto.PdfTableAreaResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTableFragmentResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTableResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTablesResponseDto;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import com.pdf2tz.backend.application.pdf.model.table.TableArea;
import com.pdf2tz.backend.application.pdf.model.table.TableCell;
import com.pdf2tz.backend.application.pdf.model.table.TableFragment;
import com.pdf2tz.backend.application.pdf.model.table.TableRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Преобразует внутреннюю модель извлечённых таблиц в DTO HTTP-ответа.
 */
@Component
public class PdfTableResponseMapper {

    /**
     * Собирает DTO ответа из списка целостных таблиц PDF-документа.
     *
     * <p>Ответ содержит не только строки таблицы, но и координаты исходных
     * фрагментов. Это делает ручку удобной для диагностики качества извлечения
     * таблиц на реальных инструкциях.</p>
     *
     * @param tables целостные таблицы документа
     * @return DTO ответа API
     */
    public PdfTablesResponseDto toResponse(List<ParsedTable> tables) {
        Objects.requireNonNull(tables, "Parsed tables must not be null");

        return new PdfTablesResponseDto(
                IntStream.range(0, tables.size())
                        .mapToObj(index -> toTableResponse(index + 1, tables.get(index)))
                        .toList()
        );
    }

    private PdfTableResponseDto toTableResponse(
            int tableNumber,
            ParsedTable table
    ) {
        return new PdfTableResponseDto(
                tableNumber,
                table.startPageNumber(),
                table.endPageNumber(),
                table.isMultiPage(),
                table.columnCount(),
                table.fragments().stream()
                        .map(this::toFragmentResponse)
                        .toList(),
                table.rows().stream()
                        .map(this::toRowResponse)
                        .toList()
        );
    }

    private PdfTableFragmentResponseDto toFragmentResponse(TableFragment fragment) {
        return new PdfTableFragmentResponseDto(
                fragment.pageNumber(),
                toAreaResponse(fragment.area()),
                fragment.rowCount()
        );
    }

    private PdfTableAreaResponseDto toAreaResponse(TableArea area) {
        return new PdfTableAreaResponseDto(
                area.top(),
                area.left(),
                area.bottom(),
                area.right(),
                area.width(),
                area.height()
        );
    }

    private List<String> toRowResponse(TableRow row) {
        return row.cells().stream()
                .map(TableCell::text)
                .toList();
    }
}
