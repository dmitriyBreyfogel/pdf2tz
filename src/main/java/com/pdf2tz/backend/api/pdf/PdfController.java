package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfPageResponseDto;
import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.PdfTextExtractor;
import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class PdfController implements PdfApi {

    private final PdfUploadReader pdfUploadReader;
    private final PdfTextExtractor textExtractor;

    public PdfController(
            PdfUploadReader pdfUploadReader,
            PdfTextExtractor textExtractor
    ) {
        this.pdfUploadReader = pdfUploadReader;
        this.textExtractor = textExtractor;
    }

    @Override
    public ResponseEntity<PdfResponseDto> extractText(MultipartFile file) {
        byte[] content = pdfUploadReader.read(file);
        ExtractedDocument document = textExtractor.extract(content);

        List<PdfPageResponseDto> pages = document.pages().stream()
                .map(page -> new PdfPageResponseDto(
                        page.pageNumber(),
                        page.text()
                ))
                .toList();

        return ResponseEntity.ok(
                new PdfResponseDto(pages)
        );
    }
}
