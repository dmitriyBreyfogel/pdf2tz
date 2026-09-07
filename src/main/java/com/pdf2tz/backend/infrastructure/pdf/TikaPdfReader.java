package com.pdf2tz.backend.infrastructure.pdf;

import com.pdf2tz.backend.application.pdf.model.ExtractedDocument;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TikaPdfReader implements PdfReaderPort {

    @Override
    public ExtractedDocument read(byte[] component) {
        try {
            PageCollectingContentHandler handler = new PageCollectingContentHandler();
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            TesseractOCRConfig ocrConfig = new TesseractOCRConfig();
            ocrConfig.setLanguage("rus+eng");
            context.set(TesseractOCRConfig.class, ocrConfig);

            AutoDetectParser parser = new AutoDetectParser();

            try (TikaInputStream input = TikaInputStream.get(component)) {
                parser.parse(input, handler, metadata, context);
            }

            return new ExtractedDocument(handler.getPages());

        } catch (Exception e) {
            throw AppException.build(
                    ErrorCode.PDF_PARSE_ERROR,
                    "Не удалось распарсить PDF файл",
                    Map.of("detail", e.getMessage())
            );
        }
    }
}
