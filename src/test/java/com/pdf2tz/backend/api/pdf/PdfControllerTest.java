package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTablesResponseDto;
import com.pdf2tz.backend.api.pdf.mapper.PdfResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfTableResponseMapper;
import com.pdf2tz.backend.application.pdf.PdfParsingPipeline;
import com.pdf2tz.backend.application.pdf.PdfTableParsingPipeline;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfControllerTest {

    private final PdfUploadReader pdfUploadReader = mock(PdfUploadReader.class);
    private final PdfParsingPipeline pdfParsingPipeline = mock(PdfParsingPipeline.class);
    private final PdfTableParsingPipeline pdfTableParsingPipeline = mock(PdfTableParsingPipeline.class);
    private final PdfResponseMapper pdfResponseMapper = mock(PdfResponseMapper.class);
    private final PdfTableResponseMapper pdfTableResponseMapper = mock(PdfTableResponseMapper.class);
    private final PdfController controller = new PdfController(
            pdfUploadReader,
            pdfParsingPipeline,
            pdfTableParsingPipeline,
            pdfResponseMapper,
            pdfTableResponseMapper
    );

    @Test
    void extractTextUsesTextParsingPipeline() {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[]{1, 2, 3};
        ParsedDocument document = mock(ParsedDocument.class);
        PdfResponseDto response = mock(PdfResponseDto.class);

        when(pdfUploadReader.read(file)).thenReturn(content);
        when(pdfParsingPipeline.parse(content)).thenReturn(document);
        when(pdfResponseMapper.toResponse(document)).thenReturn(response);

        ResponseEntity<PdfResponseDto> result = controller.extractText(file);

        assertSame(response, result.getBody());
        verify(pdfTableParsingPipeline, never()).parseTables(content);
    }

    @Test
    void extractTablesUsesTableParsingPipeline() {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[]{1, 2, 3};
        ParsedTable table = mock(ParsedTable.class);
        List<ParsedTable> tables = List.of(table);
        PdfTablesResponseDto response = mock(PdfTablesResponseDto.class);

        when(pdfUploadReader.read(file)).thenReturn(content);
        when(pdfTableParsingPipeline.parseTables(content)).thenReturn(tables);
        when(pdfTableResponseMapper.toResponse(tables)).thenReturn(response);

        ResponseEntity<PdfTablesResponseDto> result = controller.extractTables(file);

        assertSame(response, result.getBody());
        verify(pdfParsingPipeline, never()).parse(content);
    }
}
