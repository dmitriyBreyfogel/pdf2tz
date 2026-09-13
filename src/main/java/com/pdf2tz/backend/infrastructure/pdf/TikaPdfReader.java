package com.pdf2tz.backend.infrastructure.pdf;

import com.pdf2tz.backend.application.pdf.model.ExtractedTextDocument;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.OcrConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TikaPdfReader implements PdfReaderPort {

    /**
     * Явно отключает OCR для PDF-документов.
     *
     * <p>Для медицинских инструкций с нормальным текстовым слоем OCR поверх
     * страницы вреден: Tika может добавить к корректному русскому тексту второй
     * OCR-дубль, где кириллица распознана похожими латинскими символами. Поэтому
     * базовый reader берёт только нативный текст PDF. OCR-fallback стоит делать
     * отдельной стратегией только для страниц, где текстовый слой действительно
     * отсутствует или почти пустой.</p>
     */
    private static final OcrConfig.Strategy PDF_OCR_STRATEGY = OcrConfig.Strategy.NO_OCR;

    @Override
    public ExtractedTextDocument read(byte[] component) {
        try {
            PageCollectingContentHandler handler = new PageCollectingContentHandler();
            Metadata metadata = new Metadata();
            ParseContext context = createParseContext();

            AutoDetectParser parser = new AutoDetectParser();

            try (TikaInputStream input = TikaInputStream.get(component)) {
                parser.parse(input, handler, metadata, context);
            }

            return new ExtractedTextDocument(handler.getPages());

        } catch (Exception e) {
            throw AppException.build(
                    ErrorCode.PDF_PARSE_ERROR,
                    "Не удалось распарсить PDF файл",
                    Map.of("detail", e.getMessage())
            );
        }
    }

    ParseContext createParseContext() {
        ParseContext context = new ParseContext();
        PDFParserConfig pdfParserConfig = new PDFParserConfig();
        OcrConfig ocrConfig = new OcrConfig();

        ocrConfig.setStrategy(PDF_OCR_STRATEGY);
        pdfParserConfig.setOcr(ocrConfig);
        pdfParserConfig.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.NONE);

        context.set(PDFParserConfig.class, pdfParserConfig);

        return context;
    }
}
