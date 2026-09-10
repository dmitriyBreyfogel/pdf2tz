package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfTablesResponseDto;
import com.pdf2tz.backend.api.pdf.mapper.PdfResponseMapper;
import com.pdf2tz.backend.api.pdf.mapper.PdfTableResponseMapper;
import com.pdf2tz.backend.application.pdf.PdfParsingPipeline;
import com.pdf2tz.backend.application.pdf.PdfTableParsingPipeline;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.table.ParsedTable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class PdfController implements PdfApi {

    private final PdfUploadReader pdfUploadReader;
    private final PdfParsingPipeline pdfParsingPipeline;
    private final PdfTableParsingPipeline pdfTableParsingPipeline;
    private final PdfResponseMapper pdfResponseMapper;
    private final PdfTableResponseMapper pdfTableResponseMapper;

    public PdfController(
            PdfUploadReader pdfUploadReader,
            PdfParsingPipeline pdfParsingPipeline,
            PdfTableParsingPipeline pdfTableParsingPipeline,
            PdfResponseMapper pdfResponseMapper,
            PdfTableResponseMapper pdfTableResponseMapper
    ) {
        this.pdfUploadReader = pdfUploadReader;
        this.pdfParsingPipeline = pdfParsingPipeline;
        this.pdfTableParsingPipeline = pdfTableParsingPipeline;
        this.pdfResponseMapper = pdfResponseMapper;
        this.pdfTableResponseMapper = pdfTableResponseMapper;
    }

    @Override
    public ResponseEntity<PdfResponseDto> extractText(MultipartFile file) {
        byte[] content = pdfUploadReader.read(file);
        ParsedDocument document = pdfParsingPipeline.parse(content);

        return ResponseEntity.ok(pdfResponseMapper.toResponse(document));
    }

    @Override
    public ResponseEntity<PdfTablesResponseDto> extractTables(MultipartFile file) {
        byte[] content = pdfUploadReader.read(file);
        List<ParsedTable> tables = pdfTableParsingPipeline.parseTables(content);

        return ResponseEntity.ok(pdfTableResponseMapper.toResponse(tables));
    }
}
