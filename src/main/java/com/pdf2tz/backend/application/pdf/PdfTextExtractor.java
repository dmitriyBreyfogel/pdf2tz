package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.ports.PdfReaderPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class PdfTextExtractor {
    private final PdfReaderPort pdfReaderPort;

    public PdfTextExtractor(PdfReaderPort pdfReaderPort) {
        this.pdfReaderPort = pdfReaderPort;
    }

    public String extract(MultipartFile file) {
        if (file.isEmpty()) {
            throw AppException.build(
                    ErrorCode.FILE_EMPTY,
                    "Файл пуст"
            );
        }

        byte[] content;

        try {
            content = file.getBytes();
        }
        catch (Exception e) {
            throw AppException.build(
                    ErrorCode.FILE_READ_ERROR,
                    "Не удалось прочитать файл",
                    Map.of("details", e.getMessage())
            );
        }

        return pdfReaderPort.read(content);
    }
}
