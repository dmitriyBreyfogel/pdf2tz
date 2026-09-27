package com.pdf2tz.backend.application.pdf;

import com.pdf2tz.backend.application.pdf.chunking.StructuredDocumentChunker;
import com.pdf2tz.backend.application.pdf.chunking.structure.DocumentStructureAnalyzer;
import com.pdf2tz.backend.application.pdf.model.chunk.ChunkedDocument;
import com.pdf2tz.backend.application.pdf.model.document.ParsedDocument;
import com.pdf2tz.backend.application.pdf.model.structure.StructuredDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfChunkingPipelineTest {

    @Test
    void parsesOnceAndPassesResultsThroughStructureAndChunkStages() {
        byte[] content = new byte[]{1, 2, 3};
        PdfParsingPipeline parsingPipeline = mock(PdfParsingPipeline.class);
        DocumentStructureAnalyzer analyzer = mock(DocumentStructureAnalyzer.class);
        StructuredDocumentChunker chunker = mock(StructuredDocumentChunker.class);
        ParsedDocument parsedDocument = mock(ParsedDocument.class);
        StructuredDocument structuredDocument = mock(StructuredDocument.class);
        ChunkedDocument chunkedDocument = mock(ChunkedDocument.class);

        when(parsingPipeline.parse(content)).thenReturn(parsedDocument);
        when(analyzer.analyze(parsedDocument)).thenReturn(structuredDocument);
        when(chunker.chunk(structuredDocument)).thenReturn(chunkedDocument);

        PdfChunkingPipeline pipeline = new PdfChunkingPipeline(parsingPipeline, analyzer, chunker);

        assertSame(chunkedDocument, pipeline.chunk(content));
        verify(parsingPipeline).parse(content);
        verify(analyzer).analyze(parsedDocument);
        verify(chunker).chunk(structuredDocument);
    }
}
