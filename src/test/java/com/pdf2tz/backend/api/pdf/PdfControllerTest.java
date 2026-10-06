package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfChunkedDocumentResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfContextResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfParsedDocumentResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTablesResponseDto;
import com.pdf2tz.backend.api.pdf.mapper.PdfChunkedDocumentResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfContextResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfParsedDocumentResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfTableResponseMapper;
import com.pdf2tz.backend.application.pdf.PdfChunkingPipeline;
import com.pdf2tz.backend.application.pdf.PdfContextPreparationPipeline;
import com.pdf2tz.backend.application.pdf.PdfParsingPipeline;
import com.pdf2tz.backend.application.pdf.PdfTableParsingPipeline;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedTextDocument;
import com.pdf2tz.backend.application.pdf.model.retrieval.PreparedPdfContext;
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
    private final PdfChunkingPipeline pdfChunkingPipeline = mock(PdfChunkingPipeline.class);
    private final PdfContextPreparationPipeline pdfContextPreparationPipeline = mock(PdfContextPreparationPipeline.class);
    private final PdfTableParsingPipeline pdfTableParsingPipeline = mock(PdfTableParsingPipeline.class);
    private final PdfResponseMapper pdfResponseMapper = mock(PdfResponseMapper.class);
    private final PdfTableResponseMapper pdfTableResponseMapper = mock(PdfTableResponseMapper.class);
    private final PdfParsedDocumentResponseMapper pdfParsedDocumentResponseMapper = mock(
            PdfParsedDocumentResponseMapper.class
    );
    private final PdfChunkedDocumentResponseMapper pdfChunkedDocumentResponseMapper = mock(
            PdfChunkedDocumentResponseMapper.class
    );
    private final PdfContextResponseMapper pdfContextResponseMapper = mock(PdfContextResponseMapper.class);
    private final PdfController controller = new PdfController(
            pdfUploadReader,
            pdfParsingPipeline,
            pdfChunkingPipeline,
            pdfContextPreparationPipeline,
            pdfTableParsingPipeline,
            pdfResponseMapper,
            pdfTableResponseMapper,
            pdfParsedDocumentResponseMapper,
            pdfChunkedDocumentResponseMapper,
            pdfContextResponseMapper
    );

    @Test
    void extractTextUsesTextParsingPipeline() {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[]{1, 2, 3};
        CleanedTextDocument document = mock(CleanedTextDocument.class);
        PdfResponseDto response = mock(PdfResponseDto.class);

        when(pdfUploadReader.read(file)).thenReturn(content);
        when(pdfParsingPipeline.extractText(content)).thenReturn(document);
        when(pdfResponseMapper.toResponse(document)).thenReturn(response);

        ResponseEntity<PdfResponseDto> result = controller.extractText(file);

        assertSame(response, result.getBody());
        verify(pdfParsingPipeline, never()).parse(content);
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

    @Test
    void parseDocumentUsesParsingPipelineAndParsedMapper() {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[]{1, 2, 3};
        ParsedDocument document = mock(ParsedDocument.class);
        PdfParsedDocumentResponseDto response = mock(PdfParsedDocumentResponseDto.class);

        when(pdfUploadReader.read(file)).thenReturn(content);
        when(pdfParsingPipeline.parse(content)).thenReturn(document);
        when(pdfParsedDocumentResponseMapper.toResponse(document)).thenReturn(response);

        ResponseEntity<PdfParsedDocumentResponseDto> result = controller.parseDocument(file);

        assertSame(response, result.getBody());
        verify(pdfTableParsingPipeline, never()).parseTables(content);
        verify(pdfParsingPipeline, never()).extractText(content);
    }

    @Test
    void chunkDocumentUsesOnlyChunkingPipeline() {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[]{1, 2, 3};
        ChunkedDocument document = mock(ChunkedDocument.class);
        PdfChunkedDocumentResponseDto response = mock(PdfChunkedDocumentResponseDto.class);

        when(pdfUploadReader.read(file)).thenReturn(content);
        when(pdfChunkingPipeline.chunk(content)).thenReturn(document);
        when(pdfChunkedDocumentResponseMapper.toResponse(document)).thenReturn(response);

        ResponseEntity<PdfChunkedDocumentResponseDto> result = controller.chunkDocument(file);

        assertSame(response, result.getBody());
        verify(pdfChunkingPipeline).chunk(content);
        verify(pdfParsingPipeline, never()).parse(content);
        verify(pdfTableParsingPipeline, never()).parseTables(content);
    }

    @Test
    void prepareContextUsesPromptAndExistingChunkingPipeline() {
        MultipartFile file = mock(MultipartFile.class);
        byte[] content = new byte[]{1, 2, 3};
        PreparedPdfContext context = mock(PreparedPdfContext.class);
        PdfContextResponseDto response = mock(PdfContextResponseDto.class);
        when(pdfUploadReader.read(file)).thenReturn(content);
        when(pdfContextPreparationPipeline.prepare(content, "Периодичность ТО"))
                .thenReturn(context);
        when(pdfContextResponseMapper.toResponse(context)).thenReturn(response);

        ResponseEntity<PdfContextResponseDto> result = controller.prepareContext(file, "Периодичность ТО");

        assertSame(response, result.getBody());
        verify(pdfChunkingPipeline, never()).chunk(content);
        verify(pdfParsingPipeline, never()).parse(content);
    }
}
