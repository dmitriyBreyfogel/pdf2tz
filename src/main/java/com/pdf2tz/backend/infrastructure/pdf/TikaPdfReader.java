package com.pdf2tz.backend.infrastructure.pdf;

import com.pdf2tz.backend.application.pdf.model.ExtractedTextDocument;
import com.pdf2tz.backend.application.ports.PdfReaderPort;
import com.pdf2tz.backend.error.AppException;
import com.pdf2tz.backend.error.ErrorCode;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TikaPdfReader implements PdfReaderPort {

    /**
     * Настраивает Tika на чтение только текстового слоя PDF.
     *
     * <p>Изображения страницы не добавляются в результат как отдельное содержимое:
     * для текущего парсера источником правды является нативный текстовый слой PDF.
     * Такой режим не создаёт вторичные распознанные дубли и не смешивает корректный русский текст
     * с ошибочно распознанными латинскими символами.</p>
     */
    private static final PDFParserConfig.IMAGE_STRATEGY PDF_IMAGE_STRATEGY = PDFParserConfig.IMAGE_STRATEGY.NONE;

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

        pdfParserConfig.setImageStrategy(PDF_IMAGE_STRATEGY);

        context.set(PDFParserConfig.class, pdfParserConfig);

        return context;
    }
}
