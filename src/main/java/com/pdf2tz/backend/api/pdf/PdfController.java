package com.pdf2tz.backend.api.pdf;

import com.pdf2tz.backend.api.pdf.dto.PdfResponseDto;
import com.pdf2tz.backend.application.pdf.PdfTextExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PdfController implements PdfApi {

    private final PdfTextExtractor textExtractor;

    public PdfController(PdfTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    @Override
    public ResponseEntity<PdfResponseDto> extractText(MultipartFile file) {
        return ResponseEntity.status(HttpStatus.OK).body(
                new PdfResponseDto(textExtractor.extract(file))
        );
    }
}
