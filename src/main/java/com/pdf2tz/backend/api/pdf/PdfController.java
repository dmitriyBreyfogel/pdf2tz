package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.api.pdf.mapper.PdfResponseMapper;
import com.pdf2tz.backend.application.pdf.PdfTextExtractor;
import com.pdf2tz.backend.application.pdf.model.cleaning.CleanedDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PdfController implements PdfApi {

    private final PdfUploadReader pdfUploadReader;
    private final PdfTextExtractor textExtractor;
    private final PdfResponseMapper pdfResponseMapper;

    public PdfController(
            PdfUploadReader pdfUploadReader,
            PdfTextExtractor textExtractor,
            PdfResponseMapper pdfResponseMapper
    ) {
        this.pdfUploadReader = pdfUploadReader;
        this.textExtractor = textExtractor;
        this.pdfResponseMapper = pdfResponseMapper;
    }

    @Override
    public ResponseEntity<PdfResponseDto> extractText(MultipartFile file) {
        byte[] content = pdfUploadReader.read(file);
        CleanedDocument document = textExtractor.extract(content);

        return ResponseEntity.ok(pdfResponseMapper.toResponse(document));
    }
}
