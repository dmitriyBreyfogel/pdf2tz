package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.chunking.StructuredDocumentChunker;
import com.pdf2tz.backend.application.pdf.chunking.structure.DocumentStructureAnalyzer;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Координирует подготовку PDF к передаче в LLM.
 *
 * <p>Pipeline использует уже существующий parser как единственный источник
 * данных: PDF не читается повторно, watermark не чистится повторно, а таблицы
 * не извлекаются отдельной веткой.</p>
 */
@Service
public class PdfChunkingPipeline {

    private final PdfParsingPipeline pdfParsingPipeline;
    private final DocumentStructureAnalyzer documentStructureAnalyzer;
    private final StructuredDocumentChunker structuredDocumentChunker;

    public PdfChunkingPipeline(
            PdfParsingPipeline pdfParsingPipeline,
            DocumentStructureAnalyzer documentStructureAnalyzer,
            StructuredDocumentChunker structuredDocumentChunker
    ) {
        this.pdfParsingPipeline = Objects.requireNonNull(
                pdfParsingPipeline,
                "PDF parsing pipeline must not be null"
        );
        this.documentStructureAnalyzer = Objects.requireNonNull(
                documentStructureAnalyzer,
                "Document structure analyzer must not be null"
        );
        this.structuredDocumentChunker = Objects.requireNonNull(
                structuredDocumentChunker,
                "Structured document chunker must not be null"
        );
    }

    /**
     * Читает, структурирует и ограничивает документ по token budget.
     *
     * @param content байтовое представление PDF
     * @return ordered document chunks
     */
    public ChunkedDocument chunk(byte[] content) {
        Objects.requireNonNull(content, "PDF content must not be null");

        ParsedDocument parsedDocument = pdfParsingPipeline.parse(content);
        StructuredDocument structuredDocument = documentStructureAnalyzer.analyze(parsedDocument);
        return structuredDocumentChunker.chunk(structuredDocument);
    }
}
