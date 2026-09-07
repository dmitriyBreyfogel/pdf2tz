package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.ports.PdfReaderPort;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractor {
    private final PdfReaderPort pdfReaderPort;

    public PdfTextExtractor(PdfReaderPort pdfReaderPort) {
        this.pdfReaderPort = pdfReaderPort;
    }

    public String extract(byte[] content) {
        return pdfReaderPort.read(content);
    }
}
