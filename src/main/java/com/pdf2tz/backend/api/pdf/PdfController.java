package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.api.pdf.mapper.PdfResponseMapper;
import com.pdf2tz.backend.application.pdf.PdfParsingPipeline;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PdfController implements PdfApi {

    private final PdfUploadReader pdfUploadReader;
    private final PdfParsingPipeline pdfParsingPipeline;
    private final PdfResponseMapper pdfResponseMapper;

    public PdfController(
            PdfUploadReader pdfUploadReader,
            PdfParsingPipeline pdfParsingPipeline,
            PdfResponseMapper pdfResponseMapper
    ) {
        this.pdfUploadReader = pdfUploadReader;
        this.pdfParsingPipeline = pdfParsingPipeline;
        this.pdfResponseMapper = pdfResponseMapper;
    }

    @Override
    public ResponseEntity<PdfResponseDto> extractText(MultipartFile file) {
        byte[] content = pdfUploadReader.read(file);
        ParsedDocument document = pdfParsingPipeline.parse(content);

        return ResponseEntity.ok(pdfResponseMapper.toResponse(document));
    }
}
